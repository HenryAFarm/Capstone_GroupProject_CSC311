# Image Describer

The app describes an image's overall colors, lighting, orientation, and major color regions. It also uses a free MobileNetV2 image-classification model to suggest a likely subject only when the model is sufficiently confident. Separately, broad, textured green regions can be described as possibly leafy trees or other vegetation; this is intentionally a cautious visual guess, not a confirmed object detection.

The model runs on your computer and does not call Google Cloud Vision or another paid service. The image itself is never uploaded. The first analysis needs an internet connection to download the quantized model (about 3.7 MB) and its class labels. Both are cached under `~/.image-describer/models/`; later analyses run locally without internet. The model file is checked against its SHA-256 digest before use.

This is image classification, not a full image-captioning or object-detection model. Its subject suggestions can still be wrong, and it may omit a subject when confidence is low. Green color alone is not treated as a tree; the optional foliage guess requires green coverage across the image and noticeable color variation. It may still be wrong and can miss trees without green leaves, snow, small objects, text, people, and shapes.

Model: [MobileNetV2 ImageNet](https://huggingface.co/onnx-community/mobilenet_v2_1.0_224), quantized ONNX weights. It was pretrained for ImageNet classification and converted for ONNX Runtime. No API key, account, or usage charges are needed.
