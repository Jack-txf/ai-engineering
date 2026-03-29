package com.feng.rag;

import com.feng.rag.model.ModelFactory;
import com.feng.rag.model.embedding.EmbeddingResponse;
import com.feng.rag.model.siliconflow.SiliconfowModel;
import com.feng.rag.retrieval.IntentClassifier;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * @Description:
 * @Author: txf
 * @Date: 2026/3/27
 */
@SpringBootTest
public class RagTestApplication {

    @Resource
    private ModelFactory modelFactory;

    @Test
    public void testEmbedding() {
        EmbeddingResponse response = modelFactory.getModel(SiliconfowModel.SILICONFLOW)
                .embedding(List.of("hello world", "峻神、建神！！"));

    }

    @Resource
    private IntentClassifier intentClassifier;
    // 意图识别测试
    @Test
    public void testIntentClassifier() {
        System.out.println(intentClassifier.classify("请帮我写一个hello world程序"));
        System.out.println("=========");
        System.out.println(intentClassifier.classify("你好呀"));
        System.out.println("=========");
        System.out.println(intentClassifier.classify("给我推荐一个色情影片！"));
        System.out.println("=========");
        System.out.println(intentClassifier.classify("请问一下学校的校园卡补办流程是怎么样的？"));
    }
}
