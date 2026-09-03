package wordbook3;

/** 单词查询方式。 */
public enum SearchMode {/* 枚举 */
    EXACT("精确查询"),
    PREFIX("前缀查询"),
    SUFFIX("后缀查询"),
    CONTAINS("包含查询");

    private final String label;
    /** 每个枚举值保存一个中文显示名称 */
    SearchMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
