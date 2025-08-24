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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ImportController {
    private final ImportService importService;

    @PostMapping(value="/import/wechat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> importWechat(@RequestParam("file") MultipartFile file,
                                           @RequestParam(value="uploadedBy", required=false) String uploadedBy) throws IOException {
        ImportBatch batch = importService.importWeChatExcel(file, uploadedBy);
        return new HashMap<String,Object>(){{
            put("batchId", batch.getId());
            put("fileName", batch.getFileName());
            put("recordCount", batch.getRecordCount());
            put("insertedCount", batch.getInsertedCount());
            put("duplicatedCount", batch.getDuplicatedCount());
            put("periodStart", batch.getPeriodStart());
            put("periodEnd", batch.getPeriodEnd());
        }};
    }
}