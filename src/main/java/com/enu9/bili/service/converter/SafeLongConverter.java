package com.enu9.bili.service.converter;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.converters.ReadConverterContext;
import com.alibaba.excel.converters.WriteConverterContext;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.data.WriteCellData;

public class SafeLongConverter implements Converter<Long> {
    @Override
    public Class<Long> supportJavaTypeKey() {
        return Long.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public Long convertToJavaData(ReadConverterContext<?> context) {
        String value = context.getReadCellData().getStringValue();
        if (value == null || value.trim().isEmpty()) {
            return null; // 返回 null 或默认值
        }
        return Long.valueOf(value.trim());
    }

    @Override
    public WriteCellData<?> convertToExcelData(WriteConverterContext<Long> context) {
        return new WriteCellData<>(context.getValue().toString());
    }
}
