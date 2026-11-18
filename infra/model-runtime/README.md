# Model runtime

T06 uses a local llama.cpp server with Qwen3.5-9B for whole-meeting feasibility trials. Analysis verifies the pinned runtime and model, counts the rendered prompt with the runtime tokenizer and submits those exact token IDs. The feasibility profiles use one slot, no context shifting and no reasoning mode.

The runtime receives model inputs and its own service credential. It has no application SQL access or authority to publish results. OCR stops before model trials use the GPU. Private experiment records retain artifacts, hashes and launch settings.

The frozen T06 contract does not select a production serving engine, model or profile. This directory contains no production deployment configuration. See the [evaluation guide](../../docs/development/MODEL_EVALUATION.md) for the implemented developer path.
