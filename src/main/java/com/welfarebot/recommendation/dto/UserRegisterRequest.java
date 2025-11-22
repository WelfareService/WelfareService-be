package com.welfarebot.recommendation.dto;

import java.util.List;
import lombok.Data;

@Data
public class UserRegisterRequest {
    private String name;
    private Integer age;
    private String residence;
    private List<String> baseTags;
}
