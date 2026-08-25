import json
import os
from pathlib import Path

os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")

import numpy as np
import tensorflow as tf
from datasets import load_dataset

DATASET_NAME = "yunusserhat/TurkishFoods-25"
IMAGE_SIZE = int(os.getenv("IMAGE_SIZE", "160"))
BATCH_SIZE = int(os.getenv("BATCH_SIZE", "64"))
HEAD_EPOCHS = int(os.getenv("HEAD_EPOCHS", "4"))
FINE_TUNE_EPOCHS = int(os.getenv("FINE_TUNE_EPOCHS", "1"))
OUTPUT_DIR = Path(os.getenv("OUTPUT_DIR", "ml/output"))
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


def make_tf_dataset(split, training: bool):
    ds = hf[split]

    def generator():
        for sample in ds:
            image = sample["image"].convert("RGB").resize((IMAGE_SIZE, IMAGE_SIZE))
            yield np.asarray(image, dtype=np.float32), np.int32(sample["label"])

    tf_ds = tf.data.Dataset.from_generator(
        generator,
        output_signature=(
            tf.TensorSpec(shape=(IMAGE_SIZE, IMAGE_SIZE, 3), dtype=tf.float32),
            tf.TensorSpec(shape=(), dtype=tf.int32),
        ),
    )
    if training:
        tf_ds = tf_ds.shuffle(2048, seed=42, reshuffle_each_iteration=True)
    return tf_ds.batch(BATCH_SIZE).prefetch(tf.data.AUTOTUNE)


print(f"Loading {DATASET_NAME}...")
hf = load_dataset(DATASET_NAME)
label_names = hf["train"].features["label"].names
num_classes = len(label_names)
print(f"Classes: {num_classes} -> {label_names}")

train_ds = make_tf_dataset("train", training=True)
val_ds = make_tf_dataset("eval", training=False)
test_ds = make_tf_dataset("test", training=False)

augmentation = tf.keras.Sequential(
    [
        tf.keras.layers.RandomFlip("horizontal"),
        tf.keras.layers.RandomRotation(0.04),
        tf.keras.layers.RandomZoom(0.08),
        tf.keras.layers.RandomContrast(0.08),
    ],
    name="augmentation",
)

base = tf.keras.applications.MobileNetV3Small(
    input_shape=(IMAGE_SIZE, IMAGE_SIZE, 3),
    include_top=False,
    weights="imagenet",
    include_preprocessing=True,
)
base.trainable = False

inputs = tf.keras.Input(shape=(IMAGE_SIZE, IMAGE_SIZE, 3), name="image")
x = augmentation(inputs)
x = base(x, training=False)
x = tf.keras.layers.GlobalAveragePooling2D()(x)
x = tf.keras.layers.Dropout(0.25)(x)
outputs = tf.keras.layers.Dense(num_classes, activation="softmax", name="food_class")(x)
model = tf.keras.Model(inputs, outputs)

model.compile(
    optimizer=tf.keras.optimizers.Adam(1e-3),
    loss="sparse_categorical_crossentropy",
    metrics=["accuracy"],
)

callbacks = [
    tf.keras.callbacks.EarlyStopping(
        monitor="val_accuracy", patience=2, restore_best_weights=True
    )
]

print("Training classifier head...")
history_head = model.fit(
    train_ds,
    validation_data=val_ds,
    epochs=HEAD_EPOCHS,
    callbacks=callbacks,
)

if FINE_TUNE_EPOCHS > 0:
    print("Fine tuning final MobileNetV3 blocks...")
    base.trainable = True
    for layer in base.layers[:-24]:
        layer.trainable = False
    for layer in base.layers[-24:]:
        if isinstance(layer, tf.keras.layers.BatchNormalization):
            layer.trainable = False

    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-5),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    history_ft = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=FINE_TUNE_EPOCHS,
    )
else:
    history_ft = None

print("Evaluating...")
test_loss, test_accuracy = model.evaluate(test_ds, verbose=2)

print("Exporting float16 TFLite model...")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]
tflite_model = converter.convert()

model_path = OUTPUT_DIR / "turkish_food_classifier.tflite"
labels_path = OUTPUT_DIR / "turkish_food_labels.txt"
metrics_path = OUTPUT_DIR / "metrics.json"

model_path.write_bytes(tflite_model)
labels_path.write_text("\n".join(label_names) + "\n", encoding="utf-8")

metrics = {
    "dataset": DATASET_NAME,
    "image_size": IMAGE_SIZE,
    "classes": num_classes,
    "test_loss": float(test_loss),
    "test_accuracy": float(test_accuracy),
    "model_bytes": model_path.stat().st_size,
    "head_epochs_requested": HEAD_EPOCHS,
    "fine_tune_epochs_requested": FINE_TUNE_EPOCHS,
    "head_best_val_accuracy": float(max(history_head.history.get("val_accuracy", [0.0]))),
    "fine_tune_best_val_accuracy": (
        float(max(history_ft.history.get("val_accuracy", [0.0]))) if history_ft else None
    ),
}
metrics_path.write_text(json.dumps(metrics, indent=2), encoding="utf-8")
print(json.dumps(metrics, indent=2))
