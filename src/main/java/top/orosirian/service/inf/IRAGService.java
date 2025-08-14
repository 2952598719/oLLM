package top.orosirian.service.inf;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.orosirian.model.Response.TagResponseDTO;

public interface IRAGService {

    Flux<TagResponseDTO> queryRagTagList(Long userId);

    Mono<Void> createTag(Long userId, Long tagId, String tagName);

    Mono<Boolean> uploadFile(Long userId, Long tagId, Flux<FilePart> files);

    Mono<Integer> analyzeGitRepository(Long userId, Long tagId, String repoUrl, String userName, String token);

}
