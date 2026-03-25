package com.feng.rag.model;

import com.feng.rag.controller.R;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * @Description:
 * @Author: txf
 * @Date: 2026/3/24
 */
public abstract class AbstractModel {

    //----------------------------------- 对话部分 -----------------------------------
    // 方法一--同步chat测试
    public abstract R chatSync(List<Message> messages);

    // 方法二-- 流式chat测试
    public abstract SseEmitter chatStream(List<Message> messages);

    public record Message(String role, String content) {
    }

    //----------------------------------- Embedding部分 -----------------------------------


    //----------------------------------- Rerank部分 -----------------------------------

}
