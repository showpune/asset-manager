package com.microsoft.migration.assets.controller;

import com.microsoft.migration.assets.model.S3Object;
import com.microsoft.migration.assets.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/s3")
@RequiredArgsConstructor
public class S3Controller {

    private final S3Service s3Service;

    @GetMapping
    public String listObjects(Model model) {
        List<S3Object> objects = s3Service.listObjects();
        model.addAttribute("objects", objects);
        return "list";
    }

    @GetMapping("/upload")
    public String uploadForm() {
        return "upload";
    }

    @PostMapping("/upload")
    public String uploadObject(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
                return "redirect:/s3/upload";
            }

            s3Service.uploadObject(file);
            redirectAttributes.addFlashAttribute("success", "File uploaded successfully");
            return "redirect:/s3";
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Failed to upload file: " + e.getMessage());
            return "redirect:/s3/upload";
        }
    }

    @GetMapping("/view/{key}")
    public ResponseEntity<InputStreamResource> viewObject(@PathVariable String key) {
        ResponseInputStream<GetObjectResponse> objectResponse = s3Service.getObject(key);
        GetObjectResponse response = objectResponse.response();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(response.contentType()));
        headers.setContentLength(response.contentLength());
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(objectResponse));
    }

    @PostMapping("/delete/{key}")
    public String deleteObject(@PathVariable String key, RedirectAttributes redirectAttributes) {
        try {
            s3Service.deleteObject(key);
            redirectAttributes.addFlashAttribute("success", "File deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete file: " + e.getMessage());
        }
        return "redirect:/s3";
    }
}