package com.feng.rag;

import com.feng.rag.model.ModelFactory;
import com.feng.rag.model.embedding.EmbeddingResponse;
import com.feng.rag.model.siliconflow.SiliconfowModel;
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
}
