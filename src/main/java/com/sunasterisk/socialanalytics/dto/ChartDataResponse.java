package com.sunasterisk.socialanalytics.dto;

import java.util.List;

/*
VD:
{
    "labels": ["2026-07-20", "2026-07-21", "2026-07-22"],
    "datasets": [
      {
        "platform": "FACEBOOK",
        "likes":  [1200, 980, 0],
        "shares": [300,  210, 0]
      },
      {
        "platform": "TWITTER",
        "likes":  [850,  0,   400],
        "shares": [120,  0,    55]
      }
    ]
}
- labels là trục X — mỗi index tương ứng 1 ngày.
- Mỗi DatasetEntry là 1 đường trên chart — likes[i] và shares[i] là giá trị tại ngày labels[i].
- Nếu platform không có data ngày đó → giá trị 0 (padding để giữ index đồng đều).
*/

public record ChartDataResponse(
        List<String> labels,
        List<DatasetEntry> datasets
) {
    public record DatasetEntry(String platform, List<Long> likes, List<Long> shares) {}
}
