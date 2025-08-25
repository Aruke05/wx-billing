package com.enu9.bili.controller;

import com.enu9.bili.DO.ImportBatch;
import com.enu9.bili.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ImportController {
    private final ImportService importService;
    // ImportController.java
    @PostMapping(value="/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importBill(
            @RequestParam("file") List<MultipartFile> files,
            @RequestParam(value="uploadedBy", required=false) String uploadedBy
    ) throws IOException {

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("至少上传一个文件");
        }

        List<ImportBatch> batches = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f != null && !f.isEmpty()) {
                batches.add(importService.importExcel(f, uploadedBy));
            }
        }
        if (batches.isEmpty()) {
            throw new IllegalArgumentException("没有有效的文件");
        }

        // 映射每个批次
        List<Map<String, Object>> batchDtos = batches.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("batchId", b.getId());
            m.put("fileName", b.getFileName());
            m.put("recordCount", b.getRecordCount());
            m.put("insertedCount", b.getInsertedCount());
            m.put("duplicatedCount", b.getDuplicatedCount());
            m.put("periodStart", b.getPeriodStart());
            m.put("periodEnd", b.getPeriodEnd());
            return m;
        }).collect(Collectors.toList());

        // 汇总
        long recordCount = batches.stream().mapToLong(ImportBatch::getRecordCount).sum();
        long insertedCount = batches.stream().mapToLong(ImportBatch::getInsertedCount).sum();
        long duplicatedCount = batches.stream().mapToLong(ImportBatch::getDuplicatedCount).sum();

        // 时间范围聚合（可能有 null）
        LocalDateTime periodStart = batches.stream()
                .map(ImportBatch::getPeriodStart)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())   // Java 8 能正确推断
                .orElse(null);

        LocalDateTime periodEnd = batches.stream()
                .map(ImportBatch::getPeriodEnd)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("recordCount", recordCount);
        summary.put("insertedCount", insertedCount);
        summary.put("duplicatedCount", duplicatedCount);
        summary.put("periodStart", periodStart);
        summary.put("periodEnd", periodEnd);

        // 向后兼容：若只有 1 个文件，则同时输出旧结构字段
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("totalFiles", batches.size());
        resp.put("batches", batchDtos);
        resp.put("summary", summary);

        if (batches.size() == 1) {
            ImportBatch b = batches.get(0);
            resp.put("batchId", b.getId());
            resp.put("fileName", b.getFileName());
            resp.put("recordCount", b.getRecordCount());
            resp.put("insertedCount", b.getInsertedCount());
            resp.put("duplicatedCount", b.getDuplicatedCount());
            resp.put("periodStart", b.getPeriodStart());
            resp.put("periodEnd", b.getPeriodEnd());
        }

        return resp;
    }


}
