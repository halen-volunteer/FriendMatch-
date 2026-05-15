package com.zero.usercenter.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用接口返回结构。
 * 统一封装成功态、失败态、列表分页结果，便于前后端约定保持一致。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {

    /**
     * 是否成功。
     */
    private Boolean success;

    /**
     * 失败原因，仅在 success=false 时有值。
     */
    private String errorMsg;

    /**
     * 返回数据载荷。
     */
    private Object data;

    /**
     * 分页总数，非分页接口通常为 null。
     */
    private Long total;

    public static Result ok() {
        return new Result(true, null, null, null);
    }

    public static Result ok(Object data) {
        return new Result(true, null, data, null);
    }

    public static Result ok(List<?> data, Long total) {
        return new Result(true, null, data, total);
    }

    public static Result fail(String errorMsg) {
        return new Result(false, errorMsg, null, null);
    }
}
