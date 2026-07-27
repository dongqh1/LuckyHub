package com.dongqh.luckyhub.common.result;

import java.util.List;

public record PageResponse<T>(
        List<T> records,
        long total,
        long page,
        long size,
        long pages
) {
}
