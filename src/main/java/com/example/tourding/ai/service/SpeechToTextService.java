package com.example.tourding.ai.service;

import com.example.tourding.external.openai.OpenAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SpeechToTextService {
    private final OpenAiClient openAiClient;

    public String transcribe(MultipartFile audioFile) {
        return openAiClient.transcribe(audioFile);
    }
}
