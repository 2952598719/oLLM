package top.orosirian.mapper;

import org.apache.ibatis.annotations.Mapper;
import top.orosirian.model.Response.TagResponseDTO;

import java.util.List;

@Mapper
public interface RagMapper {

    List<TagResponseDTO> getTagList(Long userId);

    void createTag(Long userId, Long tagId, String tagName);

}
