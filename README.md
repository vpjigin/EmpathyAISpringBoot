# EmpathyAI

A Spring Boot application that provides real-time audio processing and AI-powered conversation capabilities using AssemblyAI for speech recognition and OpenAI for intelligent responses.

## Features

- **Real-time Audio Streaming**: WebSocket-based audio streaming with AssemblyAI integration
- **Speech-to-Text**: Live transcription using AssemblyAI's streaming API
- **AI Chat Integration**: OpenAI-powered conversational responses
- **Text-to-Speech**: OpenAI TTS service for voice responses
- **Web Interface**: Modern React-based frontend for user interaction
- **CORS Support**: Cross-origin resource sharing configuration
- **SSL Configuration**: Secure connections support

## Technologies

- **Backend**: Java 17, Spring Boot 3.1.0, Maven
- **Frontend**: React (pre-built static files)
- **AI Services**: AssemblyAI (Speech Recognition), OpenAI (Chat & TTS)
- **Communication**: WebSockets, REST APIs
- **Build Tool**: Maven

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- AssemblyAI API key
- OpenAI API key

## Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd EmpathyAI
   ```

2. **Configure API Keys**
   
   Update `src/main/resources/application.properties`:
   ```properties
   assemblyai.api.key=your_assemblyai_api_key
   openai.api.key=your_openai_api_key
   ```

3. **Build the application**
   ```bash
   mvn clean install
   ```

4. **Run the application**
   ```bash
   mvn spring-boot:run
   ```
   
   Or run the JAR directly:
   ```bash
   java -jar target/HearingAidPlotter-1.0-SNAPSHOT.jar
   ```

## Usage

1. Start the application
2. Navigate to `http://localhost:8080` in your browser
3. Use the web interface to interact with the AI assistant
4. The application will process audio input and provide AI-powered responses

## API Endpoints

- **WebSocket**: `/audio-stream-native` - Real-time audio streaming
- **REST APIs**: Various endpoints for chat and TTS functionality

## Project Structure

```
src/
├── main/
│   ├── java/com/solocrew/
│   │   ├── AppStart.java              # Main application class
│   │   ├── AppService.java            # Core application services
│   │   ├── AssemblyAIService.java     # AssemblyAI integration
│   │   ├── OpenAIChatService.java     # OpenAI chat integration
│   │   ├── OpenAITTSService.java      # Text-to-speech service
│   │   └── WebSocketConfig.java       # WebSocket configuration
│   └── resources/
│       ├── application.properties     # Configuration file
│       └── static/                    # React frontend files
```

## Configuration

The application uses the following configuration properties:

- `assemblyai.api.key`: Your AssemblyAI API key
- `openai.api.key`: Your OpenAI API key
- `spring.mvc.pathmatch.matching-strategy`: Path matching strategy

## Development

To modify the frontend, update the files in `src/main/resources/static/` or rebuild from your React source.

## License

This project is licensed under the MIT License.