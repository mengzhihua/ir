IR 供应链控制塔  v1.0.0

==============================
开箱即用
==============================

1) 服务端部署（推荐生产）
   需要 JDK 17+：
     java -jar ir-backend-1.0.0.jar --server.port=8090

   Linux systemd 示例（改路径后）：
     [Service]
     WorkingDirectory=/opt/ir
     ExecStart=/usr/bin/java -jar /opt/ir/ir-backend-1.0.0.jar --server.port=8090
     Restart=on-failure

2) Windows
   双击 start.bat
   （需已安装 JDK 17。不想装 Java 请用 GitHub Release 的 windows-x64 原生包，解压后双击 exe）

3) macOS
   双击 start.command
   若提示无法打开：右键 → 打开
   （或不装 Java，用 macos-x64 原生包里的 .app）

4) Linux
   chmod +x start.sh && ./start.sh
   （或不装 Java，用 linux-x64 原生包：./ir/bin/ir）

浏览器： http://127.0.0.1:8090
默认账号 `admin / admin123`。
数据目录：当前目录 data/
换端口： SERVER_PORT=9090 ./start.sh   或  set SERVER_PORT=9090 && start.bat

自检： SKIP_BROWSER=1 ./smoke.sh
停止： 终端里 Ctrl+C；Windows 关闭黑色窗口。
