# 说明

为方便将目录下的 markdown 文档中的图片替换为本地图片, 使用这个小工具代替手动下载.

## 使用

**需要 JDK11**

编译:

```shell
cd src
javac info/dong4j/Main.java
native-image --enable-url-protocols=https info.dong4j.Main
```

在 src 目录下会存在 info.dong4j.Main 二进制文件, 可直接运行.

## 问题

```
Accessing an URL protocol that was not enabled. The URL protocol https is supported but not enabled by default. It must be enabled by adding the --enable-url-protocols=https option to the native-image command
```

## 解决

```
native-image --enable-url-protocols=https 
```

## TODO

- [ ] 支持更多的 URL
- [ ] 解决 URL 后有参数的问题