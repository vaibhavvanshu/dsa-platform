package com.capstone.dsaplatform.dto;

import com.capstone.dsaplatform.domain.ResourceSource;

/** generated=true marks a search link the backend built itself, never a direct article/video URL. */
public record ResourceLink(String title, String url, ResourceSource source, boolean generated) {
}
