package com.maths.teacher.security;

import org.springframework.stereotype.Service;

@Service
public class VideoAccessService {

    public boolean hasAccess(String userId, Long videoId) {
        // Placeholder for paid access control rules.
        return true;
    }
}
