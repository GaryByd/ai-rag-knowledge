package com.luojiawei.trigger.http;

import com.luojiawei.api.IRAGService;
import com.luojiawei.api.response.Response;
import groovy.util.logging.Slf4j;
import jakarta.annotation.Resource;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@lombok.extern.slf4j.Slf4j
@Slf4j
@RestController
@RequestMapping(value = "/api/v1/rag/")
@CrossOrigin("*")
public class RAGController implements IRAGService {

    @Resource
    private OllamaChatClient ollamaChatClient;

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private SimpleVectorStore simpleVectorStore;

    @Resource
    private PgVectorStore pgVectorStore;

    @Resource
    private RedissonClient redissonClient;

    @RequestMapping(value = "query_rag_tag_list",method = RequestMethod.GET)
    @Override
    public Response<List<String>> queryRagTagList() {
        RList<String> elements = redissonClient.getList("ragTag");
        return Response.<List<String>>builder().code("0000")
                .info("调用成功")
                .data(elements)
                .build();
    }

    @RequestMapping(value = "file/upload",method = RequestMethod.GET,headers = "content-type=multipart/form-data")
    @Override
    public Response<String> uploadFile(@RequestParam String ragTag, @RequestParam("file") List<MultipartFile> files) {
        log.info("上传知识库{}",ragTag);
        for(MultipartFile file : files) {
            TikaDocumentReader documentsReader = new TikaDocumentReader(file.getResource());
            List<Document> documents = documentsReader.get();
            List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

            // 遍历原始文档列表，为每个文档的元数据中添加 "知识库名称"
            documents.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));
            // 遍历分割后的文档列表，为每个文档的元数据中添加 "知识库名称"
            documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));

            pgVectorStore.accept(documentSplitterList);
            RList<String> elements = redissonClient.getList(("ragTag"));
            if(!elements.contains(ragTag)) {
                elements.add(ragTag);
            }
        }
        log.info("上传知识库完成{}",ragTag);
        return Response.<String>builder().code("0000").info("调用成功").build();
    }
}
