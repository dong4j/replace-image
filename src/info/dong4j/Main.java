package info.dong4j;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final Pattern compile = Pattern.compile("!\\[.*]\\(.+\\)", Pattern.CASE_INSENSITIVE);
    private static final List<String> imagePrefixList = Stream.of(".jpg",
            ".bmp",
            ".gif",
            ".ico",
            ".pcx",
            ".jpeg",
            ".tif",
            ".png",
            ".webp").collect(Collectors.toList());


    public static void main(String[] args) throws IOException {

        // 1. 获取当前目录
        final String currentPath = System.getProperty("user.dir");
        System.out.println("工作目录: " + System.getProperty("user.dir"));

        // 2. 查找所有 markdown 文件, 包括 子目录
        final Path start = Paths.get(currentPath);

        List<Path> allMarkdownFiles = new ArrayList<>(128);

        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                System.out.println(file.getFileName());
                if (isMarkdown(file.toFile())) {
                    allMarkdownFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                System.out.println("preVisitDirectory: " + dir.getFileName());
                if (dir.getFileName().startsWith(StringPool.DOT)
                        || dir.getFileName().startsWith("assets")
                        || dir.getFileName().startsWith("img")
                        || dir.getFileName().startsWith("ima")) {

                    System.out.println("附件目录, 不再遍历");
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

        });


        if (allMarkdownFiles.size() == 0) {
            System.out.println("未找到 markdown 文件, 退出逻辑");
            System.exit(0);
        }

        allMarkdownFiles.forEach(file -> {
            try {
                // 1. 读取文件, 找到 外网图片
                final String[] document = {Files.readString(file)};
                // 2. 获取 markdown 文档的所有图片标签
                final List<String> imageLabels = getImageLabels(document[0]);

                if (imageLabels.size() > 0) {
                    Map<String, String> replaceMap = new HashMap<>(64);
                    // 2. 循环下载图片到 assets 目录
                    imageLabels.forEach(imageLabel -> {
                        String newPath = download(imageLabel, file);
                        // 保存源地址和新地址映射
                        if (isNotBlank(newPath)) {
                            replaceMap.put(imageLabel, newPath);
                        }
                    });

                    // 3. 替换原格式
                    replaceMap.forEach((old, newString) -> {
                        System.out.println("old: " + old + " new: " + newString);
                        document[0] = document[0].replace(old, newString);
                    });

                    writerFile(document[0], file.toFile());
                } else {
                    System.out.println(file + " 不存在图片标签");
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }

        });

    }

    public static boolean isMarkdown(File file) {
        return StringPool.MARKDOWN_SUFFIX.equals(getFileExtension(file));
    }


    private static String getFileExtension(File file) {
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf(StringPool.DOT);
        if (lastIndexOf == -1) {
            return "";
        }
        return name.substring(lastIndexOf);
    }


    private static List<String> getImageLabels(String htmlStr) {
        List<String> list = new ArrayList<>();
        Matcher m_image = compile.matcher(htmlStr);
        while (m_image.find()) {
            String img = m_image.group();
            System.out.println(img);

            String imgUrl = img.split("]\\(")[1];
            imgUrl = imgUrl.substring(0, imgUrl.length() - 1);
            if (imgUrl.startsWith(StringPool.PROTOCOL_HTTP) || imgUrl.startsWith(StringPool.PROTOCOL_HTTPS)) {
                String finalImgUrl = imgUrl;
                // 只需要图片后缀的标签
                imagePrefixList.stream().filter(imgUrl::contains).forEach(imagePrefix -> list.add(finalImgUrl));
            }
        }
        return list;
    }

    private static boolean isNotBlank(String str) {
        return str != null && str.length() > 0;
    }

    private static String download(String downURL, Path path) {
        try {
            // 地址
            URL url = new URL(downURL);
            // 获取文件后缀名
            String fileName = "";
            int index = url.getFile().lastIndexOf(StringPool.DOT);
            if (index != -1) {
                fileName = url.getFile().substring(index);
            }

            // 打开地址
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            // 设置图片下载超时时间
            urlConnection.setReadTimeout(2000);
            // 设置链接超时时间
            urlConnection.setConnectTimeout(3000);
            // 获取流
            InputStream is = urlConnection.getInputStream();
            // 写入流
            Random random = new Random();
            File imgDir = new File(path.toFile().getParent() + File.separator + StringPool.ATTACHMENTS_PATH);
            if (!imgDir.exists() && imgDir.mkdir()) {
                System.out.println("创建 [" + imgDir + "] 成功");
            }
            String finallyFileName = "/UrlDown" + random.nextInt(1000) + fileName;
            System.out.println("下载的文件: " + imgDir.getPath() + finallyFileName);
            FileOutputStream fos = new FileOutputStream(imgDir.getPath() + finallyFileName);

            // 写入文件
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            // 关闭流
            fos.close();
            is.close();
            urlConnection.disconnect(); // 断开连接

            // 返回下载后的图片路径
            return StringPool.DOT + File.separator + StringPool.ATTACHMENTS_PATH + finallyFileName;
        } catch (Exception e) {
            System.out.println("下载异常: " + e.getMessage());
            return "";
        }
    }

    private static void writerFile(String s, File file) {
        System.out.println("修改文件: " + file);
        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            bos.write(s.getBytes());
            System.out.println("文件修改成功！");
        } catch (IOException e) {
            System.out.println("文件修改失败！");
        }
    }


    static final class StringPool {

        public static final String DOT = ".";
        public static final String ATTACHMENTS_PATH = "assets";
        public static final String PROTOCOL_HTTP = "http://";
        public static final String PROTOCOL_HTTPS = "https://";
        public static final String MARKDOWN_TYPE = "md";
        public static final String MARKDOWN_SUFFIX = DOT + MARKDOWN_TYPE;

        public void xx() {
            
        }
    }

}