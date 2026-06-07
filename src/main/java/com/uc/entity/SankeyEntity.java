package com.uc.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 桑基图流向边实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SankeyEntity implements Serializable {

    private static final long serialVersionUID = -2142140106890956015L;
    /**
     * 起点节点名称（如：总预算）
     */
    private String source;
    /**
     * 终点节点名称（如：研发部）
     */
    private String target;
    /**
     * 流向权重值/数据量
     */
    private double value;

}