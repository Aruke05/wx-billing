// package: com.enu9.bili.service.parser
package com.enu9.bili.service.Parser;

import com.enu9.bili.DO.payTxn;
import java.io.IOException;
import java.util.List;

public interface BillParser {
    enum Channel { WECHAT, ALIPAY }
    Channel channel();
    List<payTxn> parse(byte[] bytes, Long batchId) throws IOException;
}
