package com.zero.usercenter.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用接口返回 DTO。
 *
 * <p>字段语义：</p>
 * <ul>
 *   <li>{@code success}：请求是否成功</li>
 *   <li>{@code errorMsg}：失败时的错误信息</li>
 *   <li>{@code data}：返回主体数据</li>
 *   <li>{@code total}：分页场景下的总条数</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {

    /** 是否成功。 */
    private Boolean success;

    /** 失败原因（仅 success=false 时有值）。 */
    private String errorMsg;

    /** 返回的数据载荷。 */
    private Object data;

    /** 分页总数（非分页接口通常为 null）。 */
    private Long total;

    /**
     * 成功返回（无数据体）。
     */
    public static Result ok(){
        return new Result(true, null, null, null);
    }

    /**
     * 成功返回（单数据体）。
     */
    public static Result ok(Object data){
        return new Result(true, null, data, null);
    }

    /**
     * 成功返回（分页数据）。
     */
    public static Result ok(List<?> data, Long total){
        return new Result(true, null, data, total);
    }

    /**
     * 失败返回。
     */
    public static Result fail(String errorMsg){
        return new Result(false, errorMsg, null, null);
    }
}
