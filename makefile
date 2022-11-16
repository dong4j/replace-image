javac:
	java -version
	javac src/info/dong4j/Main.java

java:javac
	native-image --enable-url-protocols=https info.dong4j.Main

