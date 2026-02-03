package com.maths.teacher.catalog.service;

import com.maths.teacher.catalog.domain.Video;
import com.maths.teacher.catalog.repository.VideoPdfRepository;
import com.maths.teacher.catalog.repository.VideoRepository;
import com.maths.teacher.catalog.web.dto.PdfResponse;
import com.maths.teacher.catalog.web.dto.SectionResponse;
import com.maths.teacher.catalog.web.dto.VideoResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VideoCatalogService {

    private static final Logger logger = LoggerFactory.getLogger(VideoCatalogService.class);

    private final VideoRepository videoRepository;
    private final VideoPdfRepository videoPdfRepository;

    public VideoCatalogService(VideoRepository videoRepository, VideoPdfRepository videoPdfRepository) {
        this.videoRepository = videoRepository;
        this.videoPdfRepository = videoPdfRepository;
    }

    public List<SectionResponse> getSections() {
        logger.info("Fetching all sections");
        return videoRepository.findAllSections()
                .stream()
                .map(SectionResponse::new)
                .toList();
    }

    public List<VideoResponse> getVideosBySection(String section) {
        logger.info("Fetching videos for section: {}", section);
        var videos = videoRepository.findBySectionOrderByDisplayOrderAsc(section);
        var pdfsByVideoId = loadPdfsByVideoId(videos);

        return videos.stream()
                .map(video -> toVideoResponse(video, pdfsByVideoId))
                .toList();
    }

    private Map<Long, List<PdfResponse>> loadPdfsByVideoId(List<Video> videos) {
        var videoIds = videos.stream()
                .map(Video::getId)
                .toList();
        if (videoIds.isEmpty()) {
            return Map.of();
        }

        return videoPdfRepository.findByVideo_IdInOrderByDisplayOrderAsc(videoIds)
                .stream()
                .collect(Collectors.groupingBy(
                        pdf -> pdf.getVideo().getId(),
                        Collectors.mapping(
                                pdf -> new PdfResponse(
                                        pdf.getId(),
                                        pdf.getTitle(),
                                        pdf.getPdfType(),
                                        pdf.getFileUrl(),
                                        pdf.getDisplayOrder()
                                ),
                                Collectors.toList()
                        )
                ));
    }

    private VideoResponse toVideoResponse(Video video, Map<Long, List<PdfResponse>> pdfsByVideoId) {
        return new VideoResponse(
                video.getId(),
                video.getVideoId(),
                video.getTitle(),
                video.getThumbnailUrl(),
                video.getDuration(),
                video.getDisplayOrder(),
                pdfsByVideoId.getOrDefault(video.getId(), List.of())
        );
    }
}
