import { Button, List, Modal, Segmented, Select, Space, Tooltip, Typography, Upload, App as AntdApp } from 'antd';
import {
  AlignCenterOutlined,
  AlignLeftOutlined,
  AlignRightOutlined,
  BgColorsOutlined,
  BoldOutlined,
  ClearOutlined,
  CodeOutlined,
  FileImageOutlined,
  FontColorsOutlined,
  FontSizeOutlined,
  ItalicOutlined,
  LineHeightOutlined,
  LinkOutlined,
  MenuOutlined,
  OrderedListOutlined,
  PictureOutlined,
  RedoOutlined,
  StrikethroughOutlined,
  UnderlineOutlined,
  UndoOutlined,
  UnorderedListOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { Extension } from '@tiptap/core';
import Image from '@tiptap/extension-image';
import TextAlign from '@tiptap/extension-text-align';
import Color from '@tiptap/extension-color';
import { BackgroundColor, TextStyle } from '@tiptap/extension-text-style';
import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent, ReactNode } from 'react';
import { api } from '../../services/api';
import { baseURL } from '../../services/http';
import { abilityDescriptionEditorContent, abilityDescriptionEditorOutput } from './abilityDescriptionEditorContent';
import {
  abilityDescriptionAlignments,
  abilityDescriptionBackgroundColors,
  abilityDescriptionBlockFormats,
  abilityDescriptionFontFamilies,
  abilityDescriptionFontSizes,
  abilityDescriptionLineHeights,
  abilityDescriptionTextColors,
  type AbilityDescriptionAlignment,
  type AbilityDescriptionBlockFormat,
  type AbilityDescriptionColorOption,
  type AbilityDescriptionFontFamily,
  type AbilityDescriptionFontSize,
  type AbilityDescriptionLineHeight,
} from './abilityDescriptionFormatOptions';
import { uploadedImageAttributes } from './abilityDescriptionImageUpload';
import { pendingWordImagesFromHtml, replacePendingWordImages } from './abilityDescriptionWordImage';

interface AbilityDescriptionEditorProps {
  value: string;
  onChange: (value: string) => void;
}

const AbilityImage = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      width: {
        default: null,
        parseHTML: (element) => element.getAttribute('width'),
        renderHTML: (attributes) => (attributes.width ? { width: attributes.width } : {}),
      },
      height: {
        default: null,
        parseHTML: (element) => element.getAttribute('height'),
        renderHTML: (attributes) => (attributes.height ? { height: attributes.height } : {}),
      },
      word_img: {
        default: null,
        parseHTML: (element) => element.getAttribute('word_img'),
        renderHTML: (attributes) => (attributes.word_img ? { word_img: attributes.word_img } : {}),
      },
      class: {
        default: null,
        parseHTML: (element) => element.getAttribute('class'),
        renderHTML: (attributes) => (attributes.class ? { class: attributes.class } : {}),
      },
    };
  },
});

const AbilityDescriptionStyles = Extension.create({
  name: 'abilityDescriptionStyles',
  addGlobalAttributes() {
    return [
      {
        types: ['textStyle'],
        attributes: {
          fontSize: {
            default: null,
            parseHTML: (element) => element.style.fontSize || null,
            renderHTML: (attributes) => (attributes.fontSize ? { style: `font-size:${attributes.fontSize}` } : {}),
          },
          fontFamily: {
            default: null,
            parseHTML: (element) => element.style.fontFamily.replace(/['"]/g, '') || null,
            renderHTML: (attributes) => (attributes.fontFamily ? { style: `font-family:${attributes.fontFamily}` } : {}),
          },
        },
      },
      {
        types: ['paragraph', 'heading'],
        attributes: {
          lineHeight: {
            default: null,
            parseHTML: (element) => element.style.lineHeight || null,
            renderHTML: (attributes) => (attributes.lineHeight ? { style: `line-height:${attributes.lineHeight}` } : {}),
          },
        },
      },
    ];
  },
});

// Tiptap replaces the original UEditor while Ant Design keeps the toolbar native to this app.
export default function AbilityDescriptionEditor({ value, onChange }: AbilityDescriptionEditorProps) {
  const { message } = AntdApp.useApp();
  const imageInputRef = useRef<HTMLInputElement>(null);
  const [uploadingImage, setUploadingImage] = useState(false);
  const [wordImageDialogOpen, setWordImageDialogOpen] = useState(false);
  const [replacingWordImages, setReplacingWordImages] = useState(false);
  const [wordImageFiles, setWordImageFiles] = useState<Map<string, File>>(() => new Map());
  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        link: {
          autolink: true,
          openOnClick: false,
        },
        underline: {},
      }),
      TextStyle,
      Color,
      BackgroundColor,
      AbilityDescriptionStyles,
      TextAlign.configure({
        types: ['heading', 'paragraph'],
      }),
      AbilityImage.configure({
        allowBase64: true,
      }),
    ],
    content: abilityDescriptionEditorContent(value),
    immediatelyRender: false,
    onUpdate: ({ editor }) => onChange(abilityDescriptionEditorOutput(editor.getHTML())),
  });

  useEffect(() => {
    if (!editor) return;
    const nextContent = abilityDescriptionEditorContent(value);
    if (editor.getHTML() !== nextContent) {
      editor.commands.setContent(nextContent, { emitUpdate: false });
    }
  }, [editor, value]);

  const pendingWordImages = editor ? pendingWordImagesFromHtml(editor.getHTML()) : [];

  if (!editor) return null;

  function currentBlockFormat(): AbilityDescriptionBlockFormat {
    if (editor?.isActive('heading', { level: 1 })) return 'heading1';
    if (editor?.isActive('heading', { level: 2 })) return 'heading2';
    if (editor?.isActive('heading', { level: 3 })) return 'heading3';
    return 'paragraph';
  }

  function applyBlockFormat(format: AbilityDescriptionBlockFormat) {
    if (format === 'heading1') {
      editor?.chain().focus().toggleHeading({ level: 1 }).run();
      return;
    }
    if (format === 'heading2') {
      editor?.chain().focus().toggleHeading({ level: 2 }).run();
      return;
    }
    if (format === 'heading3') {
      editor?.chain().focus().toggleHeading({ level: 3 }).run();
      return;
    }
    editor?.chain().focus().setParagraph().run();
  }

  function currentFontSize(): AbilityDescriptionFontSize {
    const fontSize = String(editor?.getAttributes('textStyle').fontSize ?? '');
    return abilityDescriptionFontSizes.some((option) => option.value === fontSize) ? (fontSize as AbilityDescriptionFontSize) : '';
  }

  function applyFontSize(fontSize: AbilityDescriptionFontSize) {
    editor?.chain().focus().setMark('textStyle', { fontSize: fontSize || null }).run();
  }

  function currentFontFamily(): AbilityDescriptionFontFamily {
    const fontFamily = String(editor?.getAttributes('textStyle').fontFamily ?? '').replace(/['"]/g, '');
    return abilityDescriptionFontFamilies.some((option) => option.value === fontFamily)
      ? (fontFamily as AbilityDescriptionFontFamily)
      : '';
  }

  function applyFontFamily(fontFamily: AbilityDescriptionFontFamily) {
    editor?.chain().focus().setMark('textStyle', { fontFamily: fontFamily || null }).run();
  }

  function currentLineHeight(): AbilityDescriptionLineHeight {
    const lineHeight = String(
      editor?.isActive('heading')
        ? editor.getAttributes('heading').lineHeight ?? ''
        : editor?.getAttributes('paragraph').lineHeight ?? '',
    );
    return abilityDescriptionLineHeights.some((option) => option.value === lineHeight)
      ? (lineHeight as AbilityDescriptionLineHeight)
      : '';
  }

  function applyLineHeight(lineHeight: AbilityDescriptionLineHeight) {
    const blockType = editor?.isActive('heading') ? 'heading' : 'paragraph';
    editor?.chain().focus().updateAttributes(blockType, { lineHeight: lineHeight || null }).run();
  }

  function currentAlignment(): AbilityDescriptionAlignment {
    const activeAlignment = abilityDescriptionAlignments.find((option) => editor?.isActive({ textAlign: option.value }));
    return activeAlignment?.value ?? 'left';
  }

  function applyAlignment(alignment: AbilityDescriptionAlignment) {
    editor?.chain().focus().setTextAlign(alignment).run();
  }

  function currentTextColor() {
    const color = String(editor?.getAttributes('textStyle').color ?? '').toLowerCase();
    return abilityDescriptionTextColors.some((option) => option.value === color) ? color : '';
  }

  function applyTextColor(color: string) {
    const chain = editor?.chain().focus();
    if (!chain) return;
    if (!color) {
      chain.unsetColor().run();
      return;
    }
    chain.setColor(color).run();
  }

  function currentBackgroundColor() {
    const color = String(editor?.getAttributes('textStyle').backgroundColor ?? '').toLowerCase();
    return abilityDescriptionBackgroundColors.some((option) => option.value === color) ? color : '';
  }

  function applyBackgroundColor(color: string) {
    const chain = editor?.chain().focus();
    if (!chain) return;
    if (!color || color === 'transparent') {
      chain.unsetBackgroundColor().run();
      return;
    }
    chain.setBackgroundColor(color).run();
  }

  function clearFormatting() {
    editor?.chain().focus().unsetColor().unsetBackgroundColor().unsetAllMarks().clearNodes().run();
  }

  function setLink() {
    const currentHref = String(editor?.getAttributes('link').href ?? '');
    const href = window.prompt('请输入链接地址，留空则移除链接', currentHref || 'https://');
    if (href === null) return;
    if (!href.trim()) {
      editor?.chain().focus().extendMarkRange('link').unsetLink().run();
      return;
    }
    editor?.chain().focus().extendMarkRange('link').setLink({ href: href.trim() }).run();
  }

  async function insertImage(file: File) {
    setUploadingImage(true);
    try {
      const uploaded = await api.uploadUeditorImage(file);
      const attrs = uploadedImageAttributes(uploaded, baseURL);
      editor?.chain().focus().setImage(attrs).run();
      message.success('图片已插入');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '图片上传失败');
    } finally {
      setUploadingImage(false);
    }
  }

  function selectImage(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (file) {
      void insertImage(file);
    }
  }

  function openWordImageDialog() {
    const pending = pendingWordImagesFromHtml(editor?.getHTML());
    if (!pending.length) {
      message.info('没有待替换的 Word 图片');
      return;
    }
    setWordImageFiles(new Map());
    setWordImageDialogOpen(true);
  }

  function closeWordImageDialog() {
    if (replacingWordImages) return;
    setWordImageDialogOpen(false);
    setWordImageFiles(new Map());
  }

  function selectWordImageFile(wordImage: string, file: File) {
    setWordImageFiles((current) => {
      const next = new Map(current);
      next.set(wordImage, file);
      return next;
    });
  }

  async function replaceWordImages() {
    if (!editor) return;
    const currentHtml = editor.getHTML();
    const currentPending = pendingWordImagesFromHtml(currentHtml);
    const missing = currentPending.filter((item) => !wordImageFiles.get(item.wordImage));
    if (missing.length) {
      message.warning('请选择全部待替换图片');
      return;
    }

    setReplacingWordImages(true);
    try {
      const replacements = [];
      for (const item of currentPending) {
        const file = wordImageFiles.get(item.wordImage);
        if (!file) continue;
        replacements.push({
          wordImage: item.wordImage,
          uploaded: await api.uploadUeditorImage(file),
        });
      }
      const nextHtml = replacePendingWordImages(currentHtml, replacements, baseURL);
      editor.commands.setContent(abilityDescriptionEditorContent(nextHtml), { emitUpdate: false });
      onChange(abilityDescriptionEditorOutput(nextHtml));
      setWordImageDialogOpen(false);
      setWordImageFiles(new Map());
      message.success('Word 图片已替换');
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Word 图片替换失败');
    } finally {
      setReplacingWordImages(false);
    }
  }

  return (
    <div className="ability-description-editor">
      <Space size={4} wrap className="ability-description-toolbar">
        <ToolbarButton active={false} title="撤销" icon={<UndoOutlined />} onClick={() => editor.chain().focus().undo().run()} />
        <ToolbarButton active={false} title="重做" icon={<RedoOutlined />} onClick={() => editor.chain().focus().redo().run()} />
        <Select
          aria-label="段落格式"
          size="small"
          value={currentBlockFormat()}
          options={abilityDescriptionBlockFormats}
          suffixIcon={<FontSizeOutlined />}
          style={{ width: 104 }}
          onChange={applyBlockFormat}
        />
        <Select
          aria-label="字体"
          size="small"
          value={currentFontFamily()}
          options={abilityDescriptionFontFamilies}
          style={{ width: 122 }}
          onChange={applyFontFamily}
        />
        <Select
          aria-label="字号"
          size="small"
          value={currentFontSize()}
          options={abilityDescriptionFontSizes}
          suffixIcon={<FontSizeOutlined />}
          style={{ width: 104 }}
          onChange={applyFontSize}
        />
        <Select
          aria-label="行高"
          size="small"
          value={currentLineHeight()}
          options={abilityDescriptionLineHeights}
          suffixIcon={<LineHeightOutlined />}
          style={{ width: 104 }}
          onChange={applyLineHeight}
        />
        <Segmented
          aria-label="文字对齐"
          size="small"
          value={currentAlignment()}
          options={abilityDescriptionAlignments.map((option) => ({
            value: option.value,
            label: (
              <Tooltip title={option.label}>
                <span className="ability-description-align-icon">{alignmentIcon(option.value)}</span>
              </Tooltip>
            ),
          }))}
          onChange={(alignment) => applyAlignment(alignment as AbilityDescriptionAlignment)}
        />
        <Select
          aria-label="文字颜色"
          size="small"
          value={currentTextColor()}
          options={abilityDescriptionTextColors.map(colorSelectOption)}
          suffixIcon={<FontColorsOutlined />}
          style={{ width: 108 }}
          onChange={applyTextColor}
        />
        <Select
          aria-label="背景颜色"
          size="small"
          value={currentBackgroundColor()}
          options={abilityDescriptionBackgroundColors.map(colorSelectOption)}
          suffixIcon={<BgColorsOutlined />}
          style={{ width: 108 }}
          onChange={applyBackgroundColor}
        />
        <ToolbarButton active={editor.isActive('bold')} title="加粗" icon={<BoldOutlined />} onClick={() => editor.chain().focus().toggleBold().run()} />
        <ToolbarButton active={editor.isActive('italic')} title="斜体" icon={<ItalicOutlined />} onClick={() => editor.chain().focus().toggleItalic().run()} />
        <ToolbarButton
          active={editor.isActive('underline')}
          title="下划线"
          icon={<UnderlineOutlined />}
          onClick={() => editor.chain().focus().toggleUnderline().run()}
        />
        <ToolbarButton
          active={editor.isActive('strike')}
          title="删除线"
          icon={<StrikethroughOutlined />}
          onClick={() => editor.chain().focus().toggleStrike().run()}
        />
        <ToolbarButton active={editor.isActive('code')} title="行内代码" icon={<CodeOutlined />} onClick={() => editor.chain().focus().toggleCode().run()} />
        <ToolbarButton
          active={editor.isActive('bulletList')}
          title="无序列表"
          icon={<UnorderedListOutlined />}
          onClick={() => editor.chain().focus().toggleBulletList().run()}
        />
        <ToolbarButton
          active={editor.isActive('orderedList')}
          title="有序列表"
          icon={<OrderedListOutlined />}
          onClick={() => editor.chain().focus().toggleOrderedList().run()}
        />
        <ToolbarButton
          active={editor.isActive('blockquote')}
          title="引用"
          icon={<MenuOutlined />}
          onClick={() => editor.chain().focus().toggleBlockquote().run()}
        />
        <ToolbarButton active={false} title="分隔线" icon={<MenuOutlined rotate={90} />} onClick={() => editor.chain().focus().setHorizontalRule().run()} />
        <ToolbarButton active={editor.isActive('link')} title="链接" icon={<LinkOutlined />} onClick={setLink} />
        <ToolbarButton
          active={false}
          title="图片"
          icon={<PictureOutlined />}
          loading={uploadingImage}
          onClick={() => imageInputRef.current?.click()}
        />
        <ToolbarButton
          active={pendingWordImages.length > 0}
          title="Word图片"
          icon={<FileImageOutlined />}
          loading={replacingWordImages}
          onClick={openWordImageDialog}
        />
        <ToolbarButton active={false} title="清除格式" icon={<ClearOutlined />} onClick={clearFormatting} />
      </Space>
      <input ref={imageInputRef} type="file" accept="image/*" hidden onChange={selectImage} />
      <EditorContent editor={editor} className="ability-description-editor-content" />
      <Modal
        title="Word图片"
        open={wordImageDialogOpen}
        okText="上传并替换"
        cancelText="取消"
        confirmLoading={replacingWordImages}
        okButtonProps={{ disabled: pendingWordImages.some((item) => !wordImageFiles.get(item.wordImage)) }}
        onOk={() => void replaceWordImages()}
        onCancel={closeWordImageDialog}
        width={720}
      >
        <List
          bordered
          dataSource={pendingWordImages}
          locale={{ emptyText: '没有待替换的 Word 图片' }}
          renderItem={(item) => {
            const selectedFile = wordImageFiles.get(item.wordImage);
            return (
              <List.Item
                actions={[
                  <Upload
                    key="upload"
                    accept="image/*"
                    maxCount={1}
                    showUploadList={false}
                    beforeUpload={(file) => {
                      selectWordImageFile(item.wordImage, file);
                      return false;
                    }}
                  >
                    <Button icon={<UploadOutlined />}>选择文件</Button>
                  </Upload>,
                ]}
              >
                <List.Item.Meta
                  avatar={<FileImageOutlined />}
                  title={item.label}
                  description={
                    <Space direction="vertical" size={2}>
                      <Typography.Text type="secondary">{item.wordImage}</Typography.Text>
                      <Typography.Text>{selectedFile?.name ?? '未选择'}</Typography.Text>
                    </Space>
                  }
                />
              </List.Item>
            );
          }}
        />
      </Modal>
    </div>
  );
}

function alignmentIcon(alignment: AbilityDescriptionAlignment) {
  if (alignment === 'center') return <AlignCenterOutlined />;
  if (alignment === 'right') return <AlignRightOutlined />;
  if (alignment === 'justify') return <MenuOutlined />;
  return <AlignLeftOutlined />;
}

function colorSelectOption(option: AbilityDescriptionColorOption) {
  return {
    value: option.value,
    label: (
      <Space size={6}>
        <span
          className="ability-description-color-swatch"
          style={{ backgroundColor: option.color ?? '#ffffff', borderStyle: option.color ? 'solid' : 'dashed' }}
        />
        <span>{option.label}</span>
      </Space>
    ),
  };
}

function ToolbarButton({
  active,
  title,
  icon,
  loading,
  onClick,
}: {
  active: boolean;
  title: string;
  icon: ReactNode;
  loading?: boolean;
  onClick: () => void;
}) {
  return (
    <Tooltip title={title}>
      <Button aria-label={title} size="small" type={active ? 'primary' : 'default'} icon={icon} loading={loading} onClick={onClick} />
    </Tooltip>
  );
}
