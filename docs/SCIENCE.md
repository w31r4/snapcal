# 科学依据（SCIENCE.md）

本 app 的所有计算都来自同行评审文献或官方指南。本文记录**我们采用了什么、为什么、以及不确定性有多大**。
代码中的常数与此文档一一对应（`app/src/main/java/com/snapcal/app/science/`）。

> 文献检索日期：2026-10-12。标注「未核实」的条目仅见于二手来源。

---

## 0. 结论速览

| 问题 | 结论 | 依据强度 |
|---|---|---|
| 要不要做复杂的 AI harness（多步识别→分量→查库→多次采样/多模型投票）？ | **不做**。前沿模型单次结构化调用已与多步流水线持平；多次采样取平均无收益。只做三件有证据的低成本改进：分量/参照物提示、隐形油脂单列、可选追问。 | 中（多为 2025–2026 小样本研究） |
| 基础代谢用哪个公式？ | **Mifflin-St Jeor**；有体脂率时与 Cunningham 取平均。 | 强 |
| 活动系数默认多少？ | **1.40**（中国 DRIs 轻体力；实测中国年轻人 PAL 仅 1.29）。选项用具体行为描述。 | 中-强 |
| 体重怎么预测？ | **不用 7700 kcal = 1 kg**，用 NIH 线性化动态模型。 | 强 |
| 怎样把个体误差降下来？ | **记录摄入 + 每天称重 → 反推真实维持热量**（≥ 28 天；前 2 周跳过）。 | 强 |
| 手表消耗怎么用？ | **不直接当 TDEE**。只把「比平时多出的活动」按 50% 计入，正向封顶 500。 | 中 |
| 安全护栏 | 缺口 ≤ 30%，摄入 ≥ 1500（男）/ 1200（女）；蛋白 1.2–1.6 g/kg；建议力量训练。 | 指南共识 |

---

## 1. 能量消耗估算

### 1.1 静息代谢（RMR / BMR）

采用 **Mifflin-St Jeor（Mifflin 1990）**：
- 男：`10W + 6.25H − 5A + 5`
- 女：`10W + 6.25H − 5A − 161`

理由：
- 系统综述中最可靠，非肥胖与肥胖人群 ±10% 准确率均最高（Frankenfield 2005）；在 n=337 验证中无偏，肥胖者 75% 落在 ±10% 内（Frankenfield 2013）。
- 肥胖人群系统综述推荐 MSJ，但约 25% 个体误差 > 10%（Madden 2016）。
- 中国/华人样本中偏差小：+39 kcal（Camps 2016）、+2.4%（Song 2014）、−9.7 kcal（Wu 2025，超重/肥胖女性）。
- 中国本土公式（Liu 1995、Yang 2010、Camps 2016 新加坡、Wang 2023）多基于正常体重样本、跨研究结论矛盾（例如新加坡公式一处 78% 准确、另一处 17.5%），不作默认。

体脂率已知时：Cunningham 1991 `370 + 21.6 × FFM`。中国样本中它并不更准（Xue 2019 低估 7.2%），所以**与 MSJ 取平均**而不是替换。

体脂率未知时，仅为计算「减掉的体重里脂肪占多少」，用 Deurenberg 1991 从 BMI 估算：`BF% = 1.2·BMI + 0.23·年龄 − 10.8·(男=1) − 5.4`（±4 个百分点），**不展示为测量值**。

### 1.2 每日总消耗（TDEE）

`TDEE_prior = BMR × PAL`

| 选项 | PAL | 描述 |
|---|---|---|
| 久坐（默认） | 1.40 | < 6000 步/天 |
| 轻度活跃 | 1.55 | 7000–10000 步，或每周 2–3 次运动 |
| 中度活跃 | 1.70 | > 10000 步，或每周 4–5 次训练 |
| 高度活跃 | 2.00 | 体力劳动或几乎每天高强度训练 |

- PAL 档位依据 FAO/WHO/UNU 2004 与中国 DRIs（1.40/1.70/2.00）。
- 默认最低档：自报活动量平均高估 51%（Lichtman 1992 NEJM）；西南地区年轻人双标水实测 PAL 仅 1.29（Nie 2026）。
- 个体误差：NASEM 2023 双标水公式的个体预测标准误约 342（男）/ 241（女）kcal/天 → app 显示 **±350 / ±250**。
- 参考对照：NASEM 2023 DLW 公式（美国人群，会比 MSJ×1.4 高 7–10%），暂不作默认。

### 1.3 年龄
FFM 校正后 TDEE 在 20–60 岁稳定，约 63 岁后每年下降 0.7%（Pontzer 2021 Science）。MSJ 已含年龄项，不再额外调整（60 岁以上用户待后续加入）。

---

## 2. 体重变化与计划

### 2.1 为什么不用「7700 kcal = 1 kg」
- 静态规则忽略了体重下降后消耗也会下降，严重高估长期减重（Hall & Chow 2013；Thomas 2013）。
- 前 4–6 周以糖原和水为主，每 kg 能量远低于 7700（CALERIE：第 4 周约 4858 kcal/kg，Heymsfield 2012）。

### 2.2 采用的模型（`WeightModel.kt`）
NIH 动态模型的线性化形式（Hall 2011 Lancet；Guo, Brager & Hall 2018 AJCN）：

```
ρ · dBW/dt = ΔEI − ε · ΔBW   ⇒   ΔBW(t) = (ΔEI/ε)·(1 − e^(−tε/ρ))
ρ = 9383（男）/ 9916（女）kcal/kg；ε = 28（男）/ 24（女）kcal/天/kg
```
- 每减 1 kg，消耗下降约 24–28 kcal/天；一半的最终变化约需 1 年。
- CALERIE 2 年验证中 NIH 模型平均偏差 −0.47 kg（Guo 2018）。
- 若目标超过该缺口的稳态值，app 明确提示「会在到达目标前趋于稳定」。

### 2.3 代谢适应
设计较好的研究中幅度小（−40 至 −120 kcal/天）且体重稳定后消退（Martins 2020；Nunes 2022 系统综述）。平台期主要源于依从性下降与食欲（每减 1 kg 食欲约 +100 kcal/天，Polidori 2016；Thomas 2014）。
→ 不额外硬编码适应项；由 2.4 的经验校准自动吸收。

### 2.4 自适应维持热量（`AdaptiveTdee.kt`）—— 降低个体误差的关键
```
TDEE_emp = 平均记录摄入 − ρ_eff × 体重趋势斜率（kg/天）
ρ_eff = f·9440 + (1−f)·1816,   f = 脂肪量 / (脂肪量 + 10.4)     （Forbes 定律；Hall 2008/2011）
```
- 能量平衡法：Hall & Chow 2011 AJCN（≥ 28 天每日体重可使 95% CI < 300 kcal/天）；CALERIE 中与双标水对比，群体偏差 < 40 kcal/天、个体 RMSD 215 kcal/天（Sanghvi 2015）。
- 窗口 28 天；**跳过开始记录后的前 14 天**（水/糖原期）；至少 14 天记录 + 10 次称重才启用。
- 趋势斜率用 **Theil–Sen 中位数斜率**（抗单日水分/盐分尖峰）；图上显示 α=0.1 的指数平滑线（仅展示）。
- 与公式值按数据量加权混合；偏离公式值上限 ±800 kcal。
- **重要特性**：摄入来自用户自己的拍照记录。若 AI 估算整体偏低 20%，校准出的维持热量也同步偏低，计划依然有效。因此它是「按你的记录方式计量的维持热量」，**不是生理测量值**，app 中明确这样表述。

### 2.5 每天称重
每日称重 RCT：6 个月 −6.55% vs −0.35%（Steinberg 2013），无心理负面影响（Steinberg 2014）；> 1 个月不称重预示反弹（Helander 2014）。app 首页每天提示称重，并可从体脂秤 app 经 Health Connect 导入。

### 2.6 安全护栏（`Energy.kt`、`Plan.kt`）
- 缺口选项 300 / 500 / 750 kcal；指南推荐 500–750（AHA/ACC/TOS 2013）或 500–1000（中国 2021 医学营养治疗指南）。
- 缺口 ≤ 维持热量的 30%（国家卫健委《肥胖症诊疗指南（2024 年版）》限能量平衡膳食）。
- 摄入下限 1500（男）/ 1200（女）kcal（NHLBI 1998；AHA 2013；中国 2021 指南）。这些下限是指南沿用的惯例，其推导未核实。
- 蛋白质 1.2–1.6 g/kg（BMI > 24 时按 BMI 24 对应体重计）：高蛋白饮食多保留 0.43 kg 去脂体重（Wycherley 2012）；ISSN 2017。
- 每周 2–3 次力量训练：限能量期间预防约 93% 的瘦体重流失（Sardeli 2018）；中国 2024 指南建议抗阻训练 2–3 次/周。

---

## 3. AI 拍照估算热量

### 3.1 准确度现状
| 方法 | 能量误差 | 来源 |
|---|---|---|
| 24 小时回顾（vs 双标水） | 低报 12–20% | Subar 2003 |
| 营养师看照片 | 约 41% | Thames 2021（Nutrition5k） |
| 专用 CV 模型（单张 RGB） | 26.1% | Thames 2021 |
| GPT-4o / Claude 3.5 Sonnet | 35.8% MAPE | Fridolfsson 2025 |
| GPT-4.1 / Claude 3.7 / GPT-4o（806 餐） | 23–25% MAPE | Coburn 2025 |
| GPT-5 | 30.5% MAPE | Rodríguez-Jiménez 2025 |
| 中式为主 3021 道菜 | Gemini-3-Pro 20%、GPT-5 25% | DiningBench 2026（部分真值由 LLM 生成） |

误差主要来自**分量**而不是识别（识别准确率约 93%，O'Hara 2025）；**大份量被系统性低估**（Fridolfsson 2025）；中式菜肴平均每 100 g 食材含 8.1 g 烹调油（Pu 2019），照片看不见。

### 3.2 是否需要复杂 harness —— 证据
| 改进 | 证据 | 采用？ |
|---|---|---|
| 用户一句话 / 配料提示 | MAPE 30.5% → 24.4%（简短描述）→ 13.9%（配料+用量）（Rodríguez-Jiménez 2025） | ✅ 补充说明 + 模型可选追问 |
| 按典型做法估隐形油脂 | 中餐误差 168.6 → 139.1 kcal（Coburn 2026） | ✅ 提示词要求单列「烹调用油（估）」 |
| 参照物 / 分量提示 | 「尺寸提示」降约 40 kcal（Coburn 2025） | ✅ 提示词给出盘/碗/筷子尺寸，要求先估克数 |
| 第二张照片 | 1→2 视角收益最大（DiningBench 2026） | ✅ 已支持多张（可选） |
| 营养数据库检索 / 多步 agent | 现代模型直接估计已持平或超过完整检索流水线；最佳 agent 仅小幅领先（Coburn 2026） | ❌ 不做 |
| 多次采样取平均 | 无收益（121.6 vs 121.9 kcal，Coburn 2026） | ❌ 不做 |
| 思维链 / 专家人设 | 主要帮助弱模型；对强模型不是万能药（DiningBench 2026） | 部分（营养师角色 + 步骤化提示，成本为零） |

### 3.3 精度 vs 坚持
- 自我监测与减重相关（Burke 2011 系统综述）；**记录频率**而非花在记录上的时间预测减重（Harvey 2019）。
- 拍照记录与计算卡路里减重效果相同（Dunn 2019）。
- 因此设计优先级：**低摩擦、坚持记录** > 单顿精度。系统偏差由 2.4 的自适应校准吸收。

### 3.4 不确定性展示
模型自报的 confidence 未经验证。app 显示固定的经验区间 **±30%**（基于 3.1 的 MAPE），并显示模型给出的主要不确定点。

---

## 4. 手表 / Health Connect

### 4.1 准确度
- 消费级手表能量消耗误差常 ≥ 20%，因品牌和运动类型而异；没有任何品牌准确（Fuller 2020；Shcherbina 2017；O'Driscoll 2020；Doherty 2024）。
- 自由生活下总消耗比双标水低 69–590 kcal/天（Murakami 2016）。步数相对最准（±10%）。
- 运动消耗约 28% 被基础代谢下降代偿，肥胖者更多（Careau 2021）；节食 + 有氧时 TDEE 只增加了相加模型预测的约 30%（Pontzer & Trexler 2026）。

### 4.2 采用的计算（`DailyBurn.kt`）
```
当日消耗 = 维持热量（公式或自适应校准） + 活动调整
活动调整 = 0.5 × (今日活动 − 近 28 天个人平均)，正向 ≤ +500；负向 ≥ −300 且仅对已结束的日子
```
- 手表数值只用来判断「今天比平时多动了多少」（相对变化比绝对值可信），不当作 TDEE。
- 「吃回一半运动热量」没有 RCT 验证，是结合设备误差与代偿证据的保守启发式。

### 4.3 Health Connect 陷阱（官方文档 + AOSP 源码）
- `TotalCaloriesBurned` 聚合会用**推导出的基础代谢填补空白时段**，没戴表的日子也会有一个看似合理的总数 → app 使用「总消耗 − 基础消耗」并检查数据来源。
- 只查询到「现在」，不查未来时段。
- 原始记录跨 app 不去重；聚合 API 按来源优先级去重 → 只用聚合 API。
- 三星健康只同步运动记录，不同步全天活动 → 识别后按「仅运动」处理。

---

## 6. 体脂秤与身体成分（`scale/`、`BodyComposition.kt`）

### 6.1 数据获取：米家体脂秤 S400 蓝牙广播
- 被动接收 MiBeacon v4/v5 广播（UUID 0xFE95），AES-CCM 解密（密钥为用户从自己小米账号取出的 bindkey），对象 0x6E16：体重、50 kHz 阻抗、250 kHz 阻抗、心率、成员编号。
- 协议与真值测试向量来自 Home Assistant `xiaomi-ble`（Apache-2.0）；本项目单元测试复现其真实抓包：69.9 kg / 543.2 Ω / 497.6 Ω / 92 bpm。
- 支持 S400（MJTZC01YM）、S400 Pro（MJTZC03YM，产品号 0x4B05，未经实机验证）、S200（MJTZC02YM，仅体重）。
- 每天只记录第一次称重；与上次相差 > 5 kg 视为家人称重忽略。

### 6.2 身体成分公式
**Wu 等 2015（Nutr J 14:52）**——唯一核实到系数、且在华人成人中建立的「脚对脚」阻抗公式（n=554 台湾成人，DXA 为参照，R²=0.92，SEE=3.17 kg）：
```
FFM(kg) = 13.055 + 0.204·体重 + 0.394·身高(cm)²/Z50 − 0.136·年龄 + 8.125·(男=1)
体脂率 = 1 − FFM/体重；体内水分 ≈ 0.73 × FFM（推算，不含独立信息）
```
- 个体误差：±3.2 kg FFM ≈ ±4 个体脂百分点（70–90 kg）；肥胖者 FFM 偏高约 2 kg → 体脂率偏低 2–3 点。研究设备并非小米，存在未知系统偏差。
- 不使用手到脚公式（Kyle 2001、Sun 2003）：脚对脚阻抗不含手臂和上躯干，没有公认换算（Nuñez 1997）。
- 不使用小米/openScale 逆向公式：未经同行评审，且来自旧款单频秤。
- 不显示内脏脂肪、骨量、蛋白质率、身体年龄、肌肉量、细胞内/外液：脚对脚电流不经过上半身，厂商数值是人体测量回归（openScale 中小米内脏脂肪公式甚至不含阻抗）。50 kHz/250 kHz 双频也不足以估算细胞外液（需要 ≤ 5 kHz 或频谱分析）。

### 6.3 精度与使用方式
- 下午脚对脚阻抗比上午低约 32 Ω（Oshima 2006）→ 体脂率约 −2 点；进食后 +0.9 点（Dixon 2013）；脚位置偏 5 cm 约 +37 Ω（Bousbiat 2015）。
- 因此：早上空腹、上完厕所、光脚、同一位置；只显示 **7 天平均**，周与周比较脂肪变化 < 0.7 kg 标注为「测量噪声范围内」。
- 热量目标与自适应维持热量 **只用体重**，不用体脂数据。

## 7. 「拍一顿」的校准（`PhotoCalibration.kt`）

**为什么不能用体重直接校准拍照**：体重趋势只能识别「真实摄入 − 真实消耗」。拍照低估 20% 和维持热量被高估 20% 在体重上完全等价，无法区分；而 §2.4 的自适应维持热量已经把这两种误差合并吸收。若再用体重缩放每顿饭，同一误差会被修正两次。

**采用的方法**：只用「核实过」的饭（用户按营养成分表或称重确认了真实值）作为真值：
- 系数 = exp(中位数(ln(真实值 / AI 原始估计)))，对异常值稳健；
- 以 5 个伪样本向 1.0 收缩（样本少时不轻易修正），限制在 0.7–1.6；
- ≥ 10 顿才生效；生效后同一系数同时用于当天摄入和自适应维持热量的 28 天窗口，保证两者单位一致；
- 已核实的饭不乘系数；AI 原始估计（`aiKcal`）在手动修改后仍保留，是校准的基线。

## 8. 原始数据保留
数据库 v5 起：每次 AI 调用的原始返回（含模型、提示词版本、耗时、失败）、每次上秤的完整读数（含未被采用的：当天后续称重 / 异常 / 其他成员）、删除改为软删除、每天的计算快照（当时用的维持热量、系数、目标）。可在设置中导出为 CSV（zip）。

## 5. 未做 / 待定
- 60 岁以上的额外年龄调整（Pontzer 2021）。
- 平台期提示：趋势斜率 ≈ 0 且记录缺口 ≥ 300 kcal 持续 3 周时，提示可能漏记或份量低估，而不是继续压低摄入。
- 大份量低估的个体化校正（需要用户称重数据做真值）。
- 体脂秤 BIA 体脂率误差的量化。

---

## 参考文献

**能量消耗**
- Mifflin MD et al. 1990. Am J Clin Nutr. doi:10.1093/ajcn/51.2.241
- Frankenfield D et al. 2005. J Am Diet Assoc 105:775. doi:10.1016/j.jada.2005.02.005
- Frankenfield DC 2013. Clin Nutr 32:976. doi:10.1016/j.clnu.2013.03.022
- Madden AM et al. 2016. J Hum Nutr Diet. doi:10.1111/jhn.12355
- Cunningham JJ 1991. Am J Clin Nutr. doi:10.1093/ajcn/54.6.963
- Deurenberg P et al. 1991. Br J Nutr 65:105. doi:10.1079/BJN19910073
- Camps SG et al. 2016. Nutr J 15:79. doi:10.1186/s12937-016-0197-2
- Xue J et al. 2019. IJERPH 16:2747. doi:10.3390/ijerph16152747
- Wang X et al. 2023. Nutrients 15:4185. doi:10.3390/nu15194185
- Song T et al. 2014. Obes Res Clin Pract. doi:10.1016/j.orcp.2013.05.002
- Wu X et al. 2025. Asia Pac J Clin Nutr. doi:10.6133/apjcn.202512_34(6).0013
- FAO/WHO/UNU 2004. Human Energy Requirements. https://www.fao.org/4/y5686e/y5686e07.htm
- NASEM 2023. DRIs for Energy. https://www.nationalacademies.org/read/26818/chapter/7
- Lichtman SW et al. 1992. NEJM. doi:10.1056/NEJM199212313272701
- Nie X et al. 2026. Nutrition. doi:10.1016/j.nut.2026.113350
- Pontzer H et al. 2021. Science 373:808. doi:10.1126/science.abe5017
- Careau V et al. 2021. Curr Biol. doi:10.1016/j.cub.2021.08.016
- Pontzer H & Trexler ET 2026. Curr Biol 36:1013. doi:10.1016/j.cub.2026.01.025

**体重动力学**
- Hall KD et al. 2011. Lancet 378:826. doi:10.1016/S0140-6736(11)60812-X
- Hall KD 2008. Int J Obes 32:573. https://www.nature.com/articles/0803720
- Hall KD & Chow CC 2011. Am J Clin Nutr. doi:10.3945/ajcn.111.014399
- Hall KD & Chow CC 2013. Int J Obes. doi:10.1038/ijo.2013.112
- Thomas DM et al. 2013. Int J Obes. doi:10.1038/ijo.2013.51
- Guo J, Brager DC, Hall KD 2018. Am J Clin Nutr. doi:10.1093/ajcn/nqx080
- Heymsfield SB et al. 2012. Metabolism. doi:10.1016/j.metabol.2011.11.012
- Sanghvi A et al. 2015. Am J Clin Nutr. doi:10.3945/ajcn.115.111070
- Martins C et al. 2020. Am J Clin Nutr. doi:10.1093/ajcn/nqaa220
- Nunes CL et al. 2022. Br J Nutr. doi:10.1017/S0007114521001094
- Polidori D et al. 2016. Obesity. doi:10.1002/oby.21653
- Thomas DM et al. 2014. Am J Clin Nutr. doi:10.3945/ajcn.113.079822
- Steinberg DM et al. 2013. Obesity. doi:10.1002/oby.20396
- Steinberg DM et al. 2014. Am J Prev Med. doi:10.1016/j.amepre.2013.08.006
- Helander EE et al. 2014. PLoS One. doi:10.1371/journal.pone.0113164
- Wycherley TP et al. 2012. Am J Clin Nutr. doi:10.3945/ajcn.112.044321
- Jäger R et al. 2017. JISSN. doi:10.1186/s12970-017-0177-8
- Sardeli AV et al. 2018. Nutrients 10:423
- Jensen MD et al. 2013. AHA/ACC/TOS Guideline. https://pmc.ncbi.nlm.nih.gov/articles/PMC5819889/
- 中国超重/肥胖医学营养治疗指南（2021）. 中国医学前沿杂志 13(11). doi:10.12037/YXQY.2021.11-01
- 国家卫生健康委. 肥胖症诊疗指南（2024 年版）

**AI 拍照估算**
- Subar AF et al. 2003. Am J Epidemiol. doi:10.1093/aje/kwg092
- Thames Q et al. 2021. Nutrition5k, CVPR. arXiv:2103.03375
- Fridolfsson J et al. 2025. Curr Dev Nutr. doi:10.1016/j.cdnut.2025.107556
- O'Hara C et al. 2025. Nutrients 17:607. doi:10.3390/nu17040607
- Rodríguez-Jiménez M et al. 2025. Nutrients. doi:10.3390/nu17223613
- Coburn B et al. 2025. arXiv:2507.07048
- Coburn B et al. 2026. Open-KNEAD. arXiv:2607.12911
- Jin S et al. 2026. DiningBench. arXiv:2604.10425
- Pu G et al. 2019. IJERPH. doi:10.3390/ijerph16183367
- Burke LE et al. 2011. J Am Diet Assoc. doi:10.1016/j.jada.2010.10.008
- Harvey J et al. 2019. Obesity. doi:10.1002/oby.22382
- Dunn CG et al. 2019. J Acad Nutr Diet. doi:10.1016/j.jand.2019.03.013

**体脂秤 / 生物电阻抗**
- Wu CS et al. 2015. Nutr J 14:52. doi:10.1186/s12937-015-0041-0
- Nuñez C et al. 1997. Med Sci Sports Exerc. doi:10.1097/00005768-199704000-00015
- Kyle UG et al. 2001. Nutrition. doi:10.1016/s0899-9007(00)00553-0
- Oshima Y & Shiga T 2006. Eur J Clin Nutr. doi:10.1038/sj.ejcn.1602402
- Dixon CB et al. 2013. Eur J Clin Nutr. doi:10.1038/ejcn.2013.118
- Bousbiat S et al. 2015. J Med Eng Technol. doi:10.3109/03091902.2014.973617
- Li YC et al. 2013. PLoS One. doi:10.1371/journal.pone.0058272
- Frija-Masson J et al. 2021. JMIR mHealth uHealth. doi:10.2196/22487
- Home Assistant xiaomi-ble（协议参考，Apache-2.0）：https://github.com/Bluetooth-Devices/xiaomi-ble

**可穿戴设备**
- Fuller D et al. 2020. JMIR mHealth uHealth 8:e18694. doi:10.2196/18694
- O'Driscoll R et al. 2020. Br J Sports Med. doi:10.1136/bjsports-2018-099643
- Shcherbina A et al. 2017. J Pers Med 7:3. doi:10.3390/jpm7020003
- Doherty C et al. 2024. Sports Med. doi:10.1007/s40279-024-02077-2
- Murakami H et al. 2016. JAMA Intern Med 176:702. doi:10.1001/jamainternmed.2016.0152
- Android Health Connect 文档：https://developer.android.com/health-and-fitness/health-connect/aggregate-data
