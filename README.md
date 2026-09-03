# JavaDataTypeTest

这是一个只保留核心功能的 Java Swing 英语单词簿程序，代码位于 `src/wordbook3`，包名为 `wordbook3`。

## 保留功能

- 显示单词
- 精确、前缀、后缀和包含查询
- 新增、修改、删除单词
- 播放包内 `audio` 文件夹中的 MP3 发音
- 使用包内 `data/wordbook-simple.db` 保存数据

## 运行环境

- Java 22
- SQLite JDBC：`lib/sqlite-jdbc-3.46.1.3.jar`
- JLayer MP3 播放库：`lib/jlayer-1.0.1.jar`

## 运行

在项目根目录打开 PowerShell：

```powershell
.\scripts\compile-wordbook3.ps1 -Launch
```

编译脚本会编译 `src/wordbook3`，并把包内的 `data`、`audio` 和说明文档复制到 `bin/wordbook3`。程序运行时只读取 `wordbook3` 包自己的两个资源文件夹。

详细的逐段代码解释见：[src/wordbook3/CODE_EXPLANATION.md](src/wordbook3/CODE_EXPLANATION.md)。
