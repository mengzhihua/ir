<template>
  <div class="intro">
    <header class="nav">
      <div class="brand"><el-icon><Compass /></el-icon><span>供应链控制塔</span></div>
      <el-button type="primary" @click="enter">{{ auth.token ? '进入控制塔' : '登录' }}</el-button>
    </header>

    <section class="hero">
      <p class="eyebrow">对标科捷物流公开介绍 itl.cn</p>
      <h1>订单、仓储、运配、核算之上，还有一座会讲清楚自己的控制塔</h1>
      <p class="lead">
        科捷把神州金库对外讲成四套系统：订单、仓储、运配、核算，再用案例和新闻让人记住。我们这十二套系统已经盖住这四块，并且能在成本、时效和资金盘之间做平衡。这一页把对照、已有能力和后续阶段说清楚。
      </p>
      <div class="hero-actions">
        <el-button type="primary" size="large" @click="enter">{{ auth.token ? '回到工作台' : '登录后看控制塔' }}</el-button>
        <el-button size="large" @click="scrollToPhases">看分阶段补齐</el-button>
      </div>
    </section>

    <section class="stats">
      <div v-for="item in stats" :key="item.label" class="stat">
        <strong>{{ item.value }}</strong>
        <span>{{ item.label }}</span>
      </div>
    </section>

    <section class="block">
      <h2>和金库四件套怎么对上</h2>
      <p class="note">对照的是科捷官网公开讲的能力，不是把对方的客户和仓网规模写到我们头上。</p>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>科捷公开在讲</th>
              <th>我们现在有</th>
              <th>还要补</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in compare" :key="row.theirs">
              <td>{{ row.theirs }}</td>
              <td>{{ row.ours }}</td>
              <td>{{ row.next }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="block">
      <h2>十二套系统各自站在哪</h2>
      <div class="grid">
        <article v-for="card in systems" :key="card.name" class="card">
          <p class="card-kicker">{{ card.kicker }}</p>
          <h3>{{ card.name }}</h3>
          <p>{{ card.text }}</p>
        </article>
      </div>
    </section>

    <section id="phases" class="block">
      <h2>分阶段补齐</h2>
      <div class="phases">
        <article v-for="phase in phases" :key="phase.title" class="phase">
          <p class="card-kicker">{{ phase.stage }}</p>
          <h3>{{ phase.title }}</h3>
          <ul>
            <li v-for="line in phase.lines" :key="line">{{ line }}</li>
          </ul>
        </article>
      </div>
    </section>

    <section class="cta">
      <h2>先看控制塔怎么做决定，再下到各系统执行</h2>
      <p>默认账号 admin / admin123。介绍页不需要登录。</p>
      <el-button type="primary" size="large" @click="enter">{{ auth.token ? '进入控制塔' : '登录' }}</el-button>
    </section>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { auth } from '../auth'

const router = useRouter()
const stats = [
  { value: '12', label: '套业务系统，含控制塔' },
  { value: '4', label: '个执行中枢：订单、仓储、运配、核算' },
  { value: '4', label: '档承运商择优：时效、平衡、成本、评分' },
  { value: '5', label: '档资金盘：十万到十亿，外加自定义' }
]
const compare = [
  {
    theirs: '订单中心：处理单据，带动供应、生产和物流',
    ours: 'OMS 审核、分仓预占、拆单、同一客户同一时段合单、挂起与解除、发货和签收',
    next: '合单窗口按店铺单独配置'
  },
  {
    theirs: '仓储：入库在库出库、波次拣选、序列号、包材推荐',
    ours: 'WMS 波次、序列号、箱型推荐，并按收货、上架、拣货、发运、补货、拒收计件',
    next: '计件单价按仓库和货主单独配置'
  },
  {
    theirs: '运配：智能调度、在途、签收、路径择优、承运商评级、逆向',
    ours: 'TMS 按时效、费率或历史评分择优，电子面单可实连取号，已送达订单可另开回货单',
    next: '调度台按地图把订单绑到车辆'
  },
  {
    theirs: '核算：报价、应收应付、按重量体积距离车型计费',
    ours: 'BMS 固定价、单价、阶梯和累进，也能按声明金额入账',
    next: '计费公式按距离和车型计价，说明里已经写上这两项'
  },
  {
    theirs: '官网用规模、案例和新闻介绍自己',
    ours: '这一页说明系统边界、对照和阶段，登录后进入控制塔',
    next: '把每次能力发布写成控制塔里的更新说明'
  }
]
const systems = [
  { kicker: '执行', name: 'OMS 订单', text: '接单、审核、分仓、拆单、合单、推出库，发货后记下运输和交货单号。' },
  { kicker: '执行', name: 'WMS 仓储', text: '入库、库存、波次拣选、复核装箱。计件报表按作业节点给出金额。' },
  { kicker: '执行', name: 'TMS 运配', text: '运单、发车、在途和签收。调度台按时效、成本、平衡或历史评分选承运商，报价说明写出距离和车型。电子面单可改为实连取号。' },
  { kicker: '执行', name: 'BMS 核算', text: '按合同费率或调用方给出的金额记账，运费差额可以单独入账。' },
  { kicker: '决策', name: 'IR 控制塔', text: '把十二套系统的快照放在一起，做预警、补货、资金盘沙盘和跨系统指令。' },
  { kicker: '供应', name: 'SRM / BOM / DMS', text: '采购、工程变更和渠道补货。收货数量按累计回传，避免重复加数。' },
  { kicker: '财务', name: 'SAP / INV', text: '演示财务过账、交货和进项三单匹配，开具与查验分开。' },
  { kicker: '协同', name: 'CRM / OA', text: '商机推进到谈判后单独关单。待办审批不代替业务单据往下走。' }
]
const phases = [
  {
    stage: '已经有',
    title: '执行闭环和控制塔',
    lines: [
      '订单到出库、运单、签收，仓储波次和箱型推荐',
      '承运商评分、回货单，仓库按节点计件',
      '成本与时效的平衡、人工沙盘和自动沙盘'
    ]
  },
  {
    stage: '这一阶段',
    title: '面单、合单和报价说明',
    lines: [
      '电子面单在 tms.express.mode=http 时向配置地址取号，默认仍用本地模拟号',
      '同一客户、同一地址、30 分钟内的待审核或已审核订单可以合单',
      '调度台报价说明写出距离和车型，落账账单带上车牌'
    ]
  },
  {
    stage: '下一阶段',
    title: '单价、地图和承运商生产接口',
    lines: [
      '计件单价按仓库和货主单独配置',
      '调度台按地图把订单绑到车辆',
      '电子面单接到承运商自己的生产接口，当前只连配置的取号地址'
    ]
  }
]

function enter() {
  router.push(auth.token ? '/dashboard' : '/login')
}

function scrollToPhases() {
  document.getElementById('phases')?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<style scoped>
.intro {
  min-height: 100%;
  background: #f4f7fb;
  color: #24324a;
}
.nav,
.hero,
.stats,
.block,
.cta {
  width: min(1080px, calc(100% - 32px));
  margin: 0 auto;
}
.nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 0;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #13233b;
  font-size: 20px;
  font-weight: 700;
}
.hero {
  padding: 28px 0 12px;
}
.eyebrow {
  margin: 0 0 10px;
  color: #1d65c1;
  font-weight: 600;
}
h1 {
  margin: 0;
  max-width: 18em;
  font-size: 40px;
  line-height: 1.25;
  color: #13233b;
}
.lead {
  max-width: 42em;
  font-size: 16px;
  line-height: 1.7;
  color: #52637a;
}
.hero-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  padding: 12px 0 8px;
}
.stat,
.card,
.phase {
  background: #fff;
  border-radius: 14px;
  padding: 18px;
  box-shadow: 0 8px 24px rgba(19, 35, 59, 0.05);
}
.stat strong {
  display: block;
  font-size: 28px;
  color: #1d65c1;
}
.stat span,
.note,
.card p,
.phase li,
.cta p {
  color: #52637a;
  line-height: 1.6;
}
.block {
  padding: 28px 0 8px;
}
h2 {
  margin: 0 0 8px;
  color: #13233b;
}
.table-wrap {
  overflow-x: auto;
  background: #fff;
  border-radius: 14px;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  padding: 14px 16px;
  text-align: left;
  vertical-align: top;
  border-bottom: 1px solid #e9eef5;
}
th {
  color: #13233b;
  background: #f8fbff;
}
.grid,
.phases {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.card-kicker {
  margin: 0;
  color: #1d65c1;
  font-size: 13px;
  font-weight: 600;
}
h3 {
  margin: 6px 0;
}
.phase ul {
  margin: 0;
  padding-left: 18px;
}
.cta {
  padding: 28px 0 48px;
}
@media (max-width: 800px) {
  h1 {
    font-size: 30px;
  }
  .stats,
  .grid,
  .phases {
    grid-template-columns: 1fr;
  }
}
</style>
