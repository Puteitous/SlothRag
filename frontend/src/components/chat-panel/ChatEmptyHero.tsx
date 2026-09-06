/**
 * ChatEmptyHero - 空态欢迎屏(ragbase 版)
 *
 * 从 HippoBuddy 版重写:去掉河马品牌动画与模式胶囊,
 * 改为 ragbase 标题 + 知识库问答场景的预设问题。
 */
import { useI18n } from '@/i18n';
import './ChatEmptyHero.css';

/** 预设问题(点击填充输入框) */
const PRESET_PROMPTS = [
  '员工年假有几天？',
  '公司的加班费怎么计算？',
  '入职需要准备哪些材料？',
];

interface ChatEmptyHeroProps {
  onPresetSelect: (prompt: string) => void;
}

export function ChatEmptyHero({ onPresetSelect }: ChatEmptyHeroProps) {
  const { t } = useI18n();

  return (
    <div className="chat-empty-hero">
      <div className="chat-empty-hero-title">{t('chat.heroTitle')}</div>
      <div className="chat-empty-hero-subtitle">{t('chat.heroSubtitle')}</div>
      <div className="chat-empty-hero-presets">
        {PRESET_PROMPTS.map((prompt) => (
          <button
            key={prompt}
            type="button"
            className="chat-empty-hero-preset"
            onClick={() => onPresetSelect(prompt)}
          >
            {prompt}
          </button>
        ))}
      </div>
    </div>
  );
}
