package com.enu9.bili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enu9.bili.DO.WxPayTxn;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WxPayTxnMapper   extends BaseMapper<WxPayTxn> {

    @Delete("<script>DELETE FROM wx_pay_txn WHERE order_id IN "
            + "<foreach collection='orderIds' item='id' open='(' separator=',' close=')'>"
            + "#{id}</foreach></script>")
    int deleteBatchByOrderIds(@Param("orderIds") List<String> orderIds);

}
