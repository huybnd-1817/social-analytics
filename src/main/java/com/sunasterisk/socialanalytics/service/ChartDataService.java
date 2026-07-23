package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.dto.ChartDataResponse;
import com.sunasterisk.socialanalytics.entity.SocialMetric;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import com.sunasterisk.socialanalytics.repository.SocialMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChartDataService {

    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int MAX_WINDOW_DAYS     = 90;

    private final SocialMetricRepository socialMetricRepository;

    @Transactional(readOnly = true)
    public ChartDataResponse getChartData(String platform, Instant from, Instant to, ZoneId zone) {
        if (platform != null) SocialProvider.valueOf(platform.toUpperCase()); // validate enum; throws → 400
        String platformStr = platform != null ? platform.toUpperCase() : null;

        // Chỉ truyền một đầu → 400 (tránh filter bị bỏ qua im lặng)
        if ((from == null) != (to == null)) {
            throw new IllegalArgumentException("Both 'from' and 'to' must be provided together, or omit both");
        }

        // Khi không truyền filter ngày, mặc định lấy DEFAULT_WINDOW_DAYS ngày gần nhất.
        // Khi truyền tường minh, kiểm tra không vượt quá MAX_WINDOW_DAYS để tránh full-table scan.
        Instant effectiveFrom;
        Instant effectiveTo;
        if (from == null) {
            effectiveTo   = Instant.now();
            effectiveFrom = effectiveTo.minus(DEFAULT_WINDOW_DAYS, ChronoUnit.DAYS);
        } else {
            long days = ChronoUnit.DAYS.between(from, to);
            if (days > MAX_WINDOW_DAYS) {
                throw new IllegalArgumentException(
                        "Date range must not exceed " + MAX_WINDOW_DAYS + " days (requested: " + days + ")");
            }
            effectiveFrom = from;
            effectiveTo   = to;
        }

        // Luôn dùng date-range query (PostgreSQL không infer type cho null trong prepared statement)
        boolean hasPlatform = platformStr != null;
        List<SocialMetric> metrics = hasPlatform
                ? socialMetricRepository.findByPlatformAndDateRange(platformStr, effectiveFrom, effectiveTo)
                : socialMetricRepository.findByDateRange(effectiveFrom, effectiveTo);
        return aggregate(metrics, zone);
    }

    private ChartDataResponse aggregate(List<SocialMetric> metrics, ZoneId zone) {
        /*
          TreeMap giữ ngày theo thứ tự tăng dần; EnumMap bên trong giữ thứ tự platform nhất quán
          VD:
          byDate = {
            2026-07-20 → {
                FACEBOOK  → [1200, 300],
                INSTAGRAM → [850,  120],
                TWITTER   → [400,   55]
            },
            2026-07-21 → {
                FACEBOOK  → [980,  210],
                YOUTUBE   → [3000, 890]
            },
            2026-07-22 → {
                INSTAGRAM → [1100, 430]
            }
          }
         */
        Map<LocalDate, Map<SocialProvider, long[]>> byDate = new TreeMap<>();
        // TreeSet để platform xuất hiện đúng thứ tự alphabet trong datasets
        // [FACEBOOK, INSTAGRAM, TWITTER]  ← sorted alphabetically by name()
        Set<SocialProvider> seenPlatforms = new TreeSet<>(Comparator.comparing(Enum::name));

        for (SocialMetric m : metrics) {
            LocalDate date = m.getCrawledAt().atZone(zone).toLocalDate();
            SocialProvider p = m.getPost().getPlatform();
            seenPlatforms.add(p);
            // Lấy map<platform → stats> của ngày `date`, tạo mới nếu chưa có
            byDate.computeIfAbsent(date, d -> new EnumMap<>(SocialProvider.class))
                    // Với platform `p`: nếu chưa có entry → đặt [likes, shares] mới
                    //                    nếu đã có        → cộng dồn vào entry cũ
                    .merge(p,  // key: SocialProvider
                            new long[]{m.getLikesCount(), m.getSharesCount()}, // value mới: [likes, shares]
                            (a, b) -> new long[]{a[0] + b[0], a[1] + b[1]} // nếu key đã tồn tại: cộng dồn
                    );
        }

        List<String> labels = byDate.keySet() // → lấy tất cả LocalDate keys đã có trong map, theo thứ tự tăng dần (TreeMap)
                .stream() // → chuyển Set<LocalDate> thành Stream để xử lý tuần tự
                .map(LocalDate::toString) // → chuyển mỗi LocalDate → String (vd: 2026-07-20)
                .toList(); // → thu gom kết quả thành List<String>

        List<ChartDataResponse.DatasetEntry> datasets = seenPlatforms.stream()
                .map(p -> {
                    List<Long> likes  = new ArrayList<>();
                    List<Long> shares = new ArrayList<>();
                    for (LocalDate date : byDate.keySet()) {
                        long[] vals = byDate.get(date).getOrDefault(p, new long[]{0L, 0L});
                        likes.add(vals[0]);
                        shares.add(vals[1]);
                    }
                    return new ChartDataResponse.DatasetEntry(p.name(), likes, shares);
                })
                .toList();

        return new ChartDataResponse(labels, datasets);
    }
}
