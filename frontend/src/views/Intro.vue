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
      <h2>和震坤行采购商城怎么对上</h2>
      <p class="note">对照的是 zkh.com 公开讲的找货、协议价、需求清单和批量询价，不是把对方的商品数量、仓网或送达时效写到我们头上。选品可以按工厂收窄，仍不是完整商城店面。</p>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>震坤行公开在讲</th>
              <th>我们现在有</th>
              <th>还要补</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in zkh" :key="row.theirs">
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
    ours: 'OMS 审核、分仓、拆单、合单。发运后按手机号登记短信，有邮箱再登记邮件',
    next: '承诺交期和节点超时预警'
  },
  {
    theirs: '仓储：入库在库出库、波次拣选、序列号、包材推荐',
    ours: 'WMS 波次、序列号、箱型推荐。计件按早班、中班、晚班汇总',
    next: '班次起止时间改成可配置'
  },
  {
    theirs: '运配：智能调度、在途、签收、路径择优、承运商评级、逆向',
    ours: 'TMS 择优、绑车和地图底图。取号报文区分顺丰、京东和通用格式，并标明沙箱',
    next: '面单打印预览。仍不连承运商生产环境'
  },
  {
    theirs: '核算：报价、应收应付、按重量体积距离车型计费',
    ours: 'BMS 固定价、单价、阶梯、累进，以及同一张费率表里的首重续重',
    next: '计费重量取实重和体积重的较大值'
  },
  {
    theirs: '官网用规模、案例和新闻介绍自己',
    ours: '这一页说明系统边界、对照和阶段，登录后进入控制塔',
    next: '把每次能力发布写成控制塔里的更新说明'
  }
]
const zkh = [
  {
    theirs: '按目录、品牌、制造商料号和关键词找货',
    ours: '选品按品类分组，也可以只看某个工厂允许购买的物料',
    next: '还不是完整商城店面，也没有移动采购'
  },
  {
    theirs: '协议价、折扣和批量询价后立刻下单',
    ours: '折扣档写在协议上。选品和空单价都按数量取达到的那一档',
    next: '还没有邮件审批。没有协议价仍然不显示牌价'
  },
  {
    theirs: '需求清单先收集，再核对匹配结果',
    ours: '优惠券要填生效日、失效日和可用次数。加入需求清单时扣一次',
    next: '优惠券还没写进采购订单单价'
  }
]
const systems = [
  { kicker: '执行', name: 'OMS 订单', text: '接单、审核、分仓、拆单、合单、推出库。发运后登记短信或邮件，默认不连通知网关。' },
  { kicker: '执行', name: 'WMS 仓储', text: '入库、库存、波次拣选、复核装箱。计件金额按仓库、货主和班次汇总。' },
  { kicker: '执行', name: 'TMS 运配', text: '运单、发车、在途和签收。取号报文按承运商组装，并标明这是沙箱报文。' },
  { kicker: '执行', name: 'BMS 核算', text: '同一张费率表支持固定、单价、阶梯、累进和首重续重。运费差额可以单独入账。' },
  { kicker: '决策', name: 'IR 控制塔', text: '把十二套系统的快照放在一起，做预警、补货、资金盘沙盘和跨系统指令。' },
  { kicker: '供应', name: 'SRM / BOM / DMS', text: '采购申请、询价和协议价。选品可按工厂过滤，折扣档写在协议上。优惠券有有效期和次数。未匹配的需求不转申请。' },
  { kicker: '财务', name: 'SAP / INV', text: '演示财务过账、交货和进项三单匹配，开具与查验分开。' },
  { kicker: '协同', name: 'CRM / OA', text: '商机推进到谈判后单独关单。待办审批不代替业务单据往下走。' }
]
const phases = [
  {
    stage: '已经有',
    title: '执行闭环和控制塔',
    lines: [
      '订单到出库、运单、签收。合单窗口按店铺，计件单价可按仓库和货主覆盖',
      '承运商评分、回货，按坐标绑车，运费按距离和车型加价',
      '成本与时效的平衡、人工沙盘和自动沙盘'
    ]
  },
  {
    stage: '这一阶段',
    title: '通知、班次、沙箱取号和首重续重',
    lines: [
      '发运后按手机号登记短信，客户有邮箱再登记邮件。默认不连短信或邮件网关',
      '计件按早班 8 点到 16 点、中班 16 点到 24 点、晚班汇总',
      '取号报文区分顺丰、京东和通用格式，sandbox 固定为真，仍发到配置地址',
      '核算费率表增加首重续重：首重按整段金额，超出按续重单价'
    ]
  },
  {
    stage: '下一阶段',
    title: '交期预警、计费重量和面单预览',
    lines: [
      '承诺交期和节点超时预警',
      '计费重量取实重和体积重的较大值',
      '面单打印预览。仍不连接承运商生产环境，也不写未核实的打印速度'
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
