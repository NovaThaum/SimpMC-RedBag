# SimpMC-RedBag

SimpMC-RedBag 是一个基于 Paper/Folia 和 Vault 的 Minecraft 红包插件，支持普通红包、拼手气红包、可配置税收以及精确到分的金额结算。

## 特性

- 普通红包每份金额完全相同，按整数分向下取整；无法整除的余数返还发起人。
- 拼手气红包使用整数分分配，所有红包金额总和始终等于红包池金额。
- 税率可在 `config.yml` 中配置，税款直接转入指定 Vault 账户。
- 金额最多两位小数，支持发送冷却、活动红包数量上限、最低单包金额和领取回滚。
- 同时支持 GUI 和命令发送红包，兼容 Paper/Folia。

## 命令

```text
/redbag send <金额> <份数> [normal|lucky]
/redbag open
/redbag claim <红包ID>
/redbag help
```

命令别名：`/hongbao`、`/redpacket`、`/simpmcred`。

## 税收配置

```yaml
tax:
  rate: 0.10
  recipient: Minecraft0122
```

玩家输入本金 `x` 时，实际扣款为 `x + round(x * rate, 2)`，税款转入 `recipient`。

## 构建

需要 Java 21 和 Maven：

```text
mvn -B -ntp clean package
```

构建产物位于 `target/SimpMC-RedBag-1.0.0.jar`。运行服务器需要安装 Vault 及一个 Vault 经济插件。

## 许可证与状态

这是 Minecraft0122 / SimpMC 的私有仓库项目。红包及待退款状态当前保存在内存中；插件正常关闭时会尝试结算退款，但异常崩溃后的活动状态不提供跨重启恢复。
