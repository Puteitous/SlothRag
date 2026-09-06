/**
 * InlineInput - 行内输入框(ragbase 简化版)
 *
 * 用 contenteditable div 替换 textarea,支持 Enter 发送 / Shift+Enter 换行。
 * 裁剪说明:从 HippoBuddy 版搬入,删除了文件引用芯片(@path)、图片粘贴、
 * 拖拽与点击跳文件(ragbase 无文件工作区概念)。
 *
 * 暴露方法(通过 ref):
 *  - clear(): 清空内容
 *  - focus(): 聚焦(光标移到末尾)
 *  - setContent(text): 设置纯文本内容(预设填充)
 *  - getTextContent(): 获取纯文本内容
 */
import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef } from 'react';
import './InlineInput.css';

export interface InlineInputHandle {
  clear: () => void;
  focus: () => void;
  setContent: (text: string) => void;
  getTextContent: () => string;
}

interface InlineInputProps {
  placeholder?: string;
  disabled?: boolean;
  /** Enter 触发发送(由父组件决定发送逻辑) */
  onSend: () => void;
  /** 内容是否有文字时通知父组件(用于发送按钮禁用态) */
  onContentChange?: (hasContent: boolean) => void;
}

const InlineInput = forwardRef<InlineInputHandle, InlineInputProps>((props, ref) => {
  const { placeholder = '', disabled = false, onSend, onContentChange } = props;
  const editorRef = useRef<HTMLDivElement | null>(null);
  const placeholderRef = useRef<HTMLDivElement | null>(null);
  const isComposingRef = useRef(false);

  // 更新占位符显隐
  const updatePlaceholder = useCallback(() => {
    const editor = editorRef.current;
    const ph = placeholderRef.current;
    if (!editor || !ph) return;
    const empty = !(editor.textContent || '').replace(/\u200B/g, '').trim();
    ph.style.display = empty ? '' : 'none';
    onContentChange?.(!empty);
  }, [onContentChange]);

  // 暴露方法
  useImperativeHandle(ref, () => ({
    clear() {
      const editor = editorRef.current;
      if (!editor) return;
      editor.innerHTML = '';
      updatePlaceholder();
    },

    focus() {
      const editor = editorRef.current;
      if (!editor) return;
      editor.focus();
      const range = document.createRange();
      range.selectNodeContents(editor);
      range.collapse(false);
      const sel = window.getSelection();
      if (sel) {
        sel.removeAllRanges();
        sel.addRange(range);
      }
    },

    setContent(text: string) {
      const editor = editorRef.current;
      if (!editor) return;
      editor.textContent = text;
      updatePlaceholder();
    },

    getTextContent() {
      const editor = editorRef.current;
      if (!editor) return '';
      return (editor.textContent || '').replace(/\u200B/g, '').trim();
    },
  }));

  // 键盘:Enter 发送,Shift+Enter 换行
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLDivElement>) => {
      if (isComposingRef.current) return;
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        onSend();
      }
    },
    [onSend],
  );

  // 粘贴:仅接受纯文本,剥离富文本样式,避免 DOM 被样式节点污染
  const handlePaste = useCallback((e: React.ClipboardEvent<HTMLDivElement>) => {
    e.preventDefault();
    const text = e.clipboardData.getData('text/plain');
    if (text) {
      document.execCommand('insertText', false, text);
    }
  }, []);

  const handleCompositionStart = useCallback(() => {
    isComposingRef.current = true;
  }, []);

  const handleCompositionEnd = useCallback(() => {
    isComposingRef.current = false;
  }, []);

  const handleInput = useCallback(() => {
    updatePlaceholder();
  }, [updatePlaceholder]);

  // 初始占位符
  useEffect(() => {
    updatePlaceholder();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [placeholder]);

  return (
    <div className="inline-input-wrapper">
      <div className="inline-input-placeholder" ref={placeholderRef}>
        {placeholder}
      </div>
      <div
        ref={editorRef}
        className="inline-input-editor"
        contentEditable={!disabled}
        suppressContentEditableWarning
        spellCheck={false}
        onKeyDown={handleKeyDown}
        onPaste={handlePaste}
        onCompositionStart={handleCompositionStart}
        onCompositionEnd={handleCompositionEnd}
        onInput={handleInput}
        role="textbox"
        aria-multiline="true"
        aria-placeholder={placeholder}
      />
    </div>
  );
});

InlineInput.displayName = 'InlineInput';

export default InlineInput;
