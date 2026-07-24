# 矿工伙伴-火把自动放置 (MinerPartner Torch)

自动插火把模组，专为 GT New Horizons 2.9.0 整合包开发。

## 功能

- **精准刷怪判定**：使用 `canCreatureSpawn` 与 Minecraft 刷怪逻辑完全一致，配合 F7 红黄标记
- **智能光照检测**：支持红色标记（随时刷怪）和黄色标记（夜晚刷怪）自动插火把
- **可自定义快捷键**：默认 Y 键，可在"选项-控制"中修改
- **方块黑名单**：可配置不放置火把的方块类型（如玻璃、萤石等）
- **完全客户端**：服务端无需安装，可在服务器中使用

## 使用

1. 将 `minerpartnertorch-1.2.3.jar` 放入 `.minecraft/mods/` 目录
2. 将火把放在快捷栏（0-8格）
3. 进入游戏，按 `Y` 键开启自动插火把（屏幕提示已开启）
4. 走到黑暗处自动插火把
5. 再次按 `Y` 关闭

## 配置文件

`config/minerpartnertorch.cfg` 中可配置：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| enabled | true | 启用自动插火把 |
| lightThreshold | 7 | 光照阈值（0-15），低于此值插火把 |
| scanRadius | 5 | 水平扫描半径（2-10） |
| verticalRange | 2 | 垂直扫描范围（1-5） |
| placeCooldown | 20 | 放置冷却（tick，5-100） |
| includeYellowSpawns | true | 黄色标记（夜晚刷怪）位置也插火把 |
| blockBlacklist | (见文件) | 永不放置火把的方块 ID 列表 |

快捷键在游戏内「选项-控制」→「矿工伙伴」中直接修改。

## 编译

```bash
# 需要 Java 8+ 和互联网连接
cd MinerPartner-Torch
./gradlew clean build
# 产物: build/libs/minerpartnertorch-1.2.3.jar
```

## 依赖

- Minecraft 1.7.10
- Forge 10.13.4.1614+
- GT New Horizons 2.9.0+

## 许可证

MIT License
