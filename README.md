# 微信账单分析系统

一个基于 **Java 8 + Spring Boot + MyBatis-Plus + EasyExcel + H2/SQLite** 的微信支付账单导入与可视化分析项目。  
支持上传微信导出的账单 Excel 文件，自动落库并进行多维度汇总分析（按时间段、星期、小时等），并提供前端可视化看板。
![9b4a0a0a-9246-4d34-afd7-1cac29bc2250.png](https://s2.loli.net/2025/08/24/DBUmhR3pkfF4lba.png)
---

## ✨ 功能特性
- **Excel 上传导入**
  - 支持直接上传微信官方导出的账单文件（无需手动裁剪文件头部说明）。
  - 自动识别表头，幂等去重（以交易单号为唯一键）。
- **账单查询**
  - 多条件筛选：时间范围、交易类型、对方、收支类型、金额区间等。
- **数据分析**
  - 一天内不同时间段的收入分布。
  - 一周内不同星期的收入分布。
  - 小时维度收入趋势。
- **前端看板**
  - 内置 Thymeleaf 页面，Bootstrap 布局，ECharts 图表。
  - 支持表格分页、筛选、图表联动展示。

---

## 📥 获取交易明细 Excel

在微信 App 内操作步骤如下：

1. 打开 **微信**  
2. 进入 **我** → **服务** → **钱包**  
3. 找到 **账单**  
4. 页面右上角有 **“…” 按钮**  
5. 点击进入 → **下载账单**
6. 选择 → **用作个人对账** 
7. 选择时间范围，导出并下载到本地  
8. 上传该 Excel 到本系统进行分析

---

## 🛠️ 技术栈
- Java 8  
- Spring Boot 2.7.x  
- MyBatis-Plus 3.5.x  
- EasyExcel 3.3.x  
- H2（本地文件数据库，默认） 或 SQLite  
- Thymeleaf + Bootstrap + ECharts  

---

## 🚀 本地运行

### 1. 克隆代码
```bash
git clone https://github.com/yourname/wx-bill-analyzer.git
cd wx-bill-analyzer
