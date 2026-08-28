package com.rrsistemas.erauma.moment;

import org.springframework.core.io.Resource;

public record StoredFile(Resource resource, String contentType, long sizeBytes) {}
