// package: com.enu9.bili.service
package com.enu9.bili.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.enu9.bili.DO.ImportBatch;
import com.enu9.bili.DO.WxPayTxn;
import com.enu9.bili.mapper.ImportBatchMapper;
import com.enu9.bili.mapper.WxPayTxnMapper;
import com.enu9.bili.service.Parser.BillParser;
import com.enu9.config.ExcelUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.enu9.config.ExcelUtil.*;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisStopException;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final WxPayTxnMapper txnMapper;
    private final ImportBatchMapper batchMapper;

    // 注入全部策略（Spring 会收集所有实现 BillParser 的 @Component）
    private final List<BillParser> parsers;

    @Transactional
    public ImportBatch importExcel(MultipartFile file, String uploadedBy) throws IOException {
        ImportBatch batch = new ImportBatch();
        batch.setFileName(file.getOriginalFilename());
        batch.setUploadedBy(uploadedBy);
        batch.setCreatedAt(LocalDateTime.now());
        batchMapper.insert(batch);

        byte[] bytes = toBytes(file.getInputStream());
        if (bytes.length == 0) throw new IllegalStateException("上传的Excel为空文件");

        BillParser.Channel ch = detectChannel(batch.getFileName(), bytes); // 文件名优先 + 表头兜底:contentReference[oaicite:2]{index=2}

        // 根据 channel 找策略
        BillParser parser = parsers.stream()
                .filter(p -> p.channel().equals(ch))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到解析策略: " + ch));

        List<WxPayTxn> parsed = parser.parse(bytes, batch.getId());

        if (parsed.isEmpty()) {
            batch.setRecordCount(0);
            batch.setInsertedCount(0);
            batch.setDuplicatedCount(0);
            batchMapper.updateById(batch);
            return batch;
        }

        // 统计区间 + 记录数
        LocalDateTime minT = parsed.stream().map(WxPayTxn::getTradeTime).min(LocalDateTime::compareTo).get();
        LocalDateTime maxT = parsed.stream().map(WxPayTxn::getTradeTime).max(LocalDateTime::compareTo).get();
        batch.setPeriodStart(minT);
        batch.setPeriodEnd(maxT);
        batch.setRecordCount(parsed.size());

        // 文件内 + 库内去重（仅对非空 orderId）
        int fileDup = 0;
        Map<String, WxPayTxn> byOrderId = new LinkedHashMap<>();
        for (WxPayTxn t : parsed) {
            String oid = trimToNull(t.getOrderId());
            if (oid == null) continue;
            if (byOrderId.putIfAbsent(oid, t) != null) fileDup++;
        }
        List<WxPayTxn> keep = new ArrayList<>();
        for (WxPayTxn t : parsed) if (trimToNull(t.getOrderId()) == null) keep.add(t);
        keep.addAll(byOrderId.values());

        int dbDup = 0;
        Set<String> uniqueOids = byOrderId.keySet();
        if (!uniqueOids.isEmpty()) {
            Set<String> exists = new HashSet<>();
            List<String> oids = new ArrayList<>(uniqueOids);
            for (List<String> chunk : chunks(oids, 800)) {
                if (chunk.isEmpty()) continue;
                QueryWrapper<WxPayTxn> qw = new QueryWrapper<>();
                qw.select("order_id").in("order_id", chunk);
                txnMapper.selectList(qw).forEach(e -> { if (e.getOrderId()!=null) exists.add(e.getOrderId()); });
            }
            dbDup = (int) uniqueOids.stream().filter(exists::contains).count();
            keep = keep.stream().filter(t -> {
                String oid = trimToNull(t.getOrderId());
                return oid == null || !exists.contains(oid);
            }).collect(Collectors.toList());
        }

        batch.setDuplicatedCount(fileDup + dbDup);

        for (WxPayTxn t : keep) txnMapper.insert(t);
        batch.setInsertedCount(keep.size());
        batchMapper.updateById(batch);
        return batch;
    }

    // 兼容旧入口（前端仍走 /api/import/wechat）
    @Transactional
    public ImportBatch importWeChatExcel(MultipartFile file, String uploadedBy) throws IOException {
        return importExcel(file, uploadedBy); // 复用新的分发逻辑:contentReference[oaicite:3]{index=3}
    }

    // ===== 渠道识别：文件名优先 + 表头兜底（与现有实现一致） =====
    private BillParser.Channel detectChannel(String fileName, byte[] bytes) {
        String fn = fileName == null ? "" : fileName.toLowerCase();
        if (fn.contains("wechat") || fn.contains("微信")) return BillParser.Channel.WECHAT;
        if (fn.contains("alipay") || fn.contains("支付宝")) return BillParser.Channel.ALIPAY;

        final AtomicBoolean isAli = new AtomicBoolean(false);
        AnalysisEventListener<Map<Integer, String>> header = new AnalysisEventListener<Map<Integer, String>>() {
            @Override public void invoke(Map<Integer, String> row, AnalysisContext context) {
                String all = String.join(",", row.values()
                        .stream().filter(Objects::nonNull).map(ExcelUtil::norm).collect(Collectors.toList()));
                if (all.contains("交易创建时间") || all.contains("收/支") || all.contains("交易号")) {
                    isAli.set(true);
                    throw new ExcelAnalysisStopException();
                }
                if (all.contains("交易时间") && all.contains("交易类型") && all.contains("交易对方")) {
                    isAli.set(false);
                    throw new ExcelAnalysisStopException();
                }
            }
            @Override public void doAfterAllAnalysed(AnalysisContext context) {}
        };
        try { EasyExcel.read(new ByteArrayInputStream(bytes), header).sheet().doRead(); }
        catch (ExcelAnalysisStopException ignore) {}
        return isAli.get() ? BillParser.Channel.ALIPAY : BillParser.Channel.WECHAT;
    }
}
