# Gemini on Android

This project uses a chat interface to showcase the integration and usage of various AI SDKs on Android, including real-time interactions and performance evaluation. It demonstrates capabilities such as leveraging **Google AI Client SDK** and **Firebase Vertex AI** for cloud-based tasks, **MediaPipe** for LLM processing, and **Google AI Edge SDK** for edge computing, providing a comprehensive look at Gemini technology on Android.

## Installation and Usage

### Prerequisites

- Android Studio (latest version recommended).
- Minimum Android API Level 21.
- Firebase project setup.
- Required permissions for file access if using the file picker feature.

### Installation Steps

#### General Steps

1. Clone the repository:
   ```bash
   git clone <repository_url>
   ```
2. Open the project in Android Studio.
3. Sync the Gradle files to install dependencies.
4. Build and run the application on an emulator or physical device.

#### Special Steps

##### For [Google AI Client SDK](https://ai.google.dev/gemini-api/docs)

- You need to apply an API key Google AI Studio and use it to authenticate and interact with Gemini API.

##### For [Firebase Vertex AI](https://firebase.google.com/docs/vertex-ai)

- Add your `google-services.json` file to the `app/` directory, or if the project has multiple build variants, place it under `app/src/{build_type}`.
- Configure Firebase Vertex AI to link with your Firebase project for cloud-based AI capabilities.

##### For [Gemini Nano](https://developer.android.com/ai/gemini-nano/experimental)

- Make sure you have proper physical devices or use [Firebase Device Streaming](https://firebase.google.com/docs/test-lab/android/android-device-streaming) service to connect to remote devices.
- Install necessary dependencies, incluing [Android AI Core](https://play.google.com/store/apps/details?id=com.google.android.aicore) and [Private Compute Services](https://play.google.com/store/apps/details?id=com.google.android.as.oss).

##### For [MediaPipe LLM](https://developers.googleblog.com/en/large-language-models-on-device-with-mediapipe-and-tensorflow-lite/)
- Download the required models manually from the [official MediaPipe repository](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/index#models).
- Place the downloaded models in `/data/local/tmp/llm/model.bin` on your device. The `gemma2-2b-it-gpu-int8` model is recommended.

### Usage Example

After installing the app, you can use it to chat with various Gemini models and attach local files for context. The app provides a flexible interface to experiment with different AI capabilities. Additionally, you can:

- Use the **Settings** menu to switch between different usage modes and configure parameters to suit your needs.
- Explore real-time AI-generated responses based on your prompts.
- Attach local files to enhance the AI's understanding of the context.
- Monitor key performance metrics like response time, character counts, and token counts directly within the app.

## Contribution

Contributions are welcome! Follow these steps:

1. Fork this repository.
2. Create a branch: `git checkout -b feature/<feature_name>`.
3. Commit your changes: `git commit -m '<commit_message>`.
4. Push to the branch: `git push origin feature/<feature_name>`.
5. Create a Pull Request.

## Contact

For questions or suggestions, feel free to reach out:

- [dailystudio2010@gmail.com](mailto\:dailystudio2010@gmail.com)

## Assets License

Assets with the prefix _illustration_ in the directory `core/src/main/res/drawable-nodpi` use the [Vecteezy Pro License](https://support.vecteezy.com/en_us/new-vecteezy-licensing-ByHivesvt). Please ensure you have obtained the proper rights before using them, or replace them with your own assets.

## License

This project is distributed under the [Apache 2.0 License](./LICENSE).

