#!/bin/bash
# 启动带 CDP 调试端口的 Chrome（数模爬虫用）
# 用法: bash start_cdp_chrome.sh
# 步骤:
#   1) 先完全退出 Flatpak 版 Chrome（右上角菜单退出，或 pkill -f "com.google.Chrome"）
#   2) 运行本脚本
#   3) 在打开的 Chrome 里登录 知乎(zhihu.com) 与 小红书(xiaohongshu.com)
#   4) 登录完成后即可交给 Claude 采集

PROFILE="${HOME}/kb-cdp-profile"
mkdir -p "$PROFILE"

# --- 检查 9222 端口占用 ---
if ss -tln 2>/dev/null | grep -q ":9222"; then
  OWNER=$(ps -p $(ss -tlnp 2>/dev/null | grep ":9222" | grep -oP 'pid=\K[0-9]+' | head -1) -o comm= 2>/dev/null)
  echo "[!] 9222 端口被占用（进程: $OWNER）"
  if pgrep -f "kb-cdp-profile" >/dev/null; then
    echo "    是旧调试实例，正在退出…"
    pkill -f "kb-cdp-profile"
    sleep 3
  else
    echo "    是其他 Chrome 实例（可能是 Flatpak 版 Chrome）"
    echo "    请先完全退出 Chrome：chrome://settings → 退出，或执行:"
    echo "    pkill -f com.google.Chrome"
    echo "    然后重新运行本脚本"
    exit 1
  fi
fi

echo "[*] 启动 Chrome（调试端口 9222，独立配置目录 $PROFILE）…"
google-chrome-stable --remote-debugging-port=9222 \
  --user-data-dir="$PROFILE" \
  --no-first-run --no-default-browser-check \
  https://www.zhihu.com/ https://www.xiaohongshu.com/ &

sleep 5
echo "[*] 验证调试端口:"
curl -s http://127.0.0.1:9222/json/version | head -c 150
echo
echo "[✓] 请在打开的 Chrome 窗口登录知乎与小红书，完成后回来告诉我"
