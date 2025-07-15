package com.luojiawei.dev.tech.test;

import com.alibaba.fastjson.JSON;
import com.luojiawei.tech.Application;
import groovy.util.logging.Slf4j;
import jakarta.annotation.Resource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class RAGTest {
    @Resource
    private OllamaChatClient ollamaChatClient;

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private SimpleVectorStore simpleVectorStore;

    @Resource
    private PgVectorStore pgVectorStore;

    @Test
    public void upload() {
        // 创建一个 TikaDocumentReader 对象，读取指定路径下的文本文件
        TikaDocumentReader reader = new TikaDocumentReader("./data/file.txt");

        // 获取读取到的文档列表
        List<Document> documents = reader.get();

        // 使用 tokenTextSplitter 对文档进行分割，得到新的文档列表
        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

        // 遍历原始文档列表，为每个文档的元数据中添加 "知识库名称"
        documents.forEach(doc -> doc.getMetadata().put("knowledge", "知识库名称"));

        // 遍历分割后的文档列表，为每个文档的元数据中添加 "知识库名称"
        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", "知识库名称"));

        // 将分割后的文档上传到 pgVectorStore 存储中
        pgVectorStore.accept(documentSplitterList);

        // 输出上传完成的消息
        System.out.println("上传完成");
    }


    public void chat() {
        // 定义输入的用户消息
        String message = "王大瓜，哪年出生";

        // 定义系统提示模板，告诉模型如何回答问题，注意必须返回中文且只使用已知文档信息
        String SYSTEM_PROMPT = """
            Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
            If unsure, simply state that you don't know.
            Another thing you need to note is that your reply must be in Chinese!
            DOCUMENTS:
                {documents}
            """;

        // 构造搜索请求，查询与输入消息相关的文档
        // `withTopK(5)` 表示返回与用户输入消息最相关的 5 个文档
        // `withFilterExpression("knowledge == '知识库名称'")` 用于过滤出知识库名称为特定值的文档
        SearchRequest request = SearchRequest.query(message).withTopK(5).withFilterExpression("knowledge == '知识库名称'");

        // 使用 `pgVectorStore` 执行相似度搜索，返回与输入消息最相关的文档列表
        List<Document> documents = pgVectorStore.similaritySearch(request);

        // 将搜索到的文档内容连接成一个字符串，作为系统提示模板中的 `{documents}` 部分
        String documentsCollectors = documents.stream().map(Document::getContent).collect(Collectors.joining());

        // 创建一个系统提示消息对象，传入已拼接的文档内容
        // 系统提示消息将作为模板中的 `DOCUMENTS` 部分，用来提供准确的回答
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentsCollectors));

        // 创建消息列表，包含用户输入的消息和模型需要处理的系统提示消息
        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));  // 用户消息
        messages.add(ragMessage);               // 系统消息

        // 使用 Ollama 的 `ollamaChatClient` 调用对话接口，传入消息列表，并指定模型为 `deepseek-r1:1.5b`
        // 该接口会返回一个 ChatResponse 对象，包含模型的回答
        ChatResponse chatResponse = ollamaChatClient.call(new Prompt(messages, OllamaOptions.create().withModel("deepseek-r1:1.5b")));

        // 输出测试结果，打印模型的回答
        System.out.println("测试结果:" + JSON.toJSONString(chatResponse));
    }

}
