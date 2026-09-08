/**
 * i18n 文案(精简版)
 *
 * slothrag 只保留聊天界面用到的 key,新增时在此追加。
 */
export type Lang = 'zh' | 'en';

const zh: Record<string, string> = {
  // 代码块/公式
  'chatui.copy': '复制',
  'chatui.copied': '已复制',
  'chatui.copyLatex': '复制 LaTeX',

  // 消息气泡
  'chat.sourcesTitle': '来源引用 ({count})',
  'chat.expandFullText': '展开全文',
  'chat.collapseContent': '收起内容',

  // 聊天面板
  'chat.appTitle': 'slothrag 知识库问答',
  'chat.selectKb': '选择知识库',
  'chat.newSession': '新建会话',
  'chat.error': '错误',
  'chat.scrollToBottom': '回到底部',
  'chat.inputPlaceholder': '向知识库提问…（Enter 发送，Shift+Enter 换行）',
  'chat.sendMessage': '发送',
  'chat.stop': '停止',
  'chat.heroTitle': '你好，我是 SlothRag 知识库助手',
  'chat.heroSubtitle': '基于已入库文档回答你的问题，答案附带来源引用',
  'chat.themeLabel': '主题：{theme}',
  'chat.historyTitle': '历史会话',
  'chat.historyEmpty': '暂无历史会话',
  'chat.historyDelete': '删除会话',
  'chat.historyConfirmDelete': '确定删除该会话吗？此操作不可恢复',

  // 消息反馈
  'chat.feedback.helpful': '有帮助',
  'chat.feedback.notHelpful': '没帮助',
  'chat.feedback.thanks': '感谢你的反馈',
  'chat.feedback.retract': '已取消反馈',

  // 推荐问题
  'chat.recommendedTitle': '你可能还想问',

  // 应用导航
  'app.nav.chat': '聊天',
  'app.nav.admin': '管理后台',
  'app.title': 'SlothRag',
};

/** 英文未单独维护,回退中文 */
const en: Record<string, string> = {};

export { zh, en };
