package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.dto.MetricResponse;
import com.sunasterisk.socialanalytics.repository.SocialMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetricService {

    private final SocialMetricRepository socialMetricRepository;

    @Transactional(readOnly = true)
    public Page<MetricResponse> findAll(Pageable pageable) {
        return socialMetricRepository.findAll(pageable)
                .map(MetricResponse::from);
    }
}
