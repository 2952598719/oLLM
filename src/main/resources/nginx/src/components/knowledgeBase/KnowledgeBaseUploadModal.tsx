import { useState, useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import { cn } from '@/lib/utils';
import { toast } from 'sonner';

// API Types
interface TagResponseDTO {
  tagId: string;
  tagName: string;
}

interface KnowledgeBaseUploadModalProps {
  isOpen: boolean;
  onClose: () => void;
}

// API Service Functions
const fetchTagList = async (): Promise<TagResponseDTO[]> => {
  const response = await fetch('http://localhost:8090/api/v1/rag/query_tag_list', {
    credentials: 'include',
  });
  
  if (!response.ok) {
    throw new Error('Failed to fetch tag list');
  }
  
  return response.json();
};

const createTag = async (tagName: string): Promise<string> => {
  const response = await fetch(`http://localhost:8090/api/v1/rag/create_tag?tagName=${encodeURIComponent(tagName)}`, {
    method: 'POST',
    credentials: 'include',
  });
  
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(errorText || 'Failed to create tag');
  }
  
  return response.text();
};

const uploadFiles = async (tagId: string, files: File[]): Promise<string> => {
  const formData = new FormData();
  formData.append('tagId', tagId);
  
  files.forEach(file => {
    formData.append('files', file);
  });
  
  const response = await fetch('http://localhost:8090/api/v1/rag/file/upload', {
    method: 'POST',
    credentials: 'include',
    body: formData,
  });
  
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(errorText || 'Failed to upload files');
  }
  
  return response.text();
};

const analyzeGitRepository = async (repoUrl: string, userName: string, token: string): Promise<string> => {
  const response = await fetch('http://localhost:8090/api/v1/rag/analyze_git_repository', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({
      repoUrl,
      userName,
      token,
    }),
  });
  
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(errorText || 'Failed to analyze Git repository');
  }
  
  return response.text();
};

export default function KnowledgeBaseUploadModal({ isOpen, onClose }: KnowledgeBaseUploadModalProps) {
  // State for tabs
  const [activeTab, setActiveTab] = useState<'file' | 'git'>('file');
  
  // State for file upload tab
  const [isInputMode, setIsInputMode] = useState(true); // true: input, false: dropdown
  const [tagName, setTagName] = useState('');
  const [selectedTagId, setSelectedTagId] = useState('');
  const [tagList, setTagList] = useState<TagResponseDTO[]>([]);
  const [files, setFiles] = useState<File[]>([]);
  const [isLoadingTags, setIsLoadingTags] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  
  // State for Git repository tab
  const [repoUrl, setRepoUrl] = useState('');
  const [gitUsername, setGitUsername] = useState('');
  const [gitToken, setGitToken] = useState('');
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  
  // Refs for file upload
  const fileInputRef = useRef<HTMLInputElement>(null);
  const dropZoneRef = useRef<HTMLDivElement>(null);
  const [isDragging, setIsDragging] = useState(false);
  
  // Fetch tag list when switching to dropdown mode
  useEffect(() => {
    if (!isInputMode && tagList.length === 0) {
      loadTagList();
    }
  }, [isInputMode]);
  
  // Load tag list from API
  const loadTagList = async () => {
    setIsLoadingTags(true);
    try {
      const tags = await fetchTagList();
      setTagList(tags);
    } catch (error) {
      console.error('Error loading tag list:', error);
      toast.error('加载标签列表失败，请重试');
    } finally {
      setIsLoadingTags(false);
    }
  };
  
  // Toggle between input and dropdown modes
  const toggleMode = () => {
    setIsInputMode(!isInputMode);
    if (!isInputMode) {
      // Switching to input mode
      setTagName('');
    } else {
      // Switching to dropdown mode
      setSelectedTagId('');
      loadTagList();
    }
  };
  
  // Handle file selection
  const handleFileSelect = (selectedFiles: FileList | null) => {
    if (!selectedFiles) return;
    
    const newFiles = Array.from(selectedFiles);
    setFiles(prev => [...prev, ...newFiles]);
  };
  
  // Handle drag and drop events
  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };
  
  const handleDragLeave = () => {
    setIsDragging(false);
  };
  
  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    handleFileSelect(e.dataTransfer.files);
  };
  
  // Remove a file from the list
  const removeFile = (indexToRemove: number) => {
    setFiles(prev => prev.filter((_, index) => index !== indexToRemove));
  };
  
  // Handle file upload
  const handleFileUpload = async () => {
    if (files.length === 0) {
      toast.error('请选择文件后再上传');
      return;
    }
    
    // Validate based on current mode
    if (isInputMode) {
      // Input mode - create new tag first
      if (!tagName.trim()) {
        toast.error('请输入标签名称');
        return;
      }
    } else {
      // Dropdown mode - must select a tag
      if (!selectedTagId || selectedTagId === 'default') {
        toast.error('请选择一个标签');
        return;
      }
    }
    
    setIsUploading(true);
    try {
      let tagId = selectedTagId;
      
      // Create new tag if in input mode
      if (isInputMode) {
        tagId = await createTag(tagName.trim());
      }
      
      // Upload files
      const result = await uploadFiles(tagId, files);
      toast.success(result || '文件上传成功');
      
      // Reset form
      setFiles([]);
      setTagName('');
      onClose();
    } catch (error) {
      console.error('Error uploading files:', error);
      toast.error(error instanceof Error ? error.message : '文件上传失败，请重试');
    } finally {
      setIsUploading(false);
    }
  };
  
  // Handle Git repository analysis
  const handleAnalyzeGitRepo = async () => {
    if (!repoUrl.trim() || !gitUsername.trim() || !gitToken.trim()) {
      toast.error('请填写所有必填字段');
      return;
    }
    
    setIsAnalyzing(true);
    try {
      const result = await analyzeGitRepository(repoUrl.trim(), gitUsername.trim(), gitToken.trim());
      toast.success(result || 'Git仓库解析成功');
      
      // Reset form
      setRepoUrl('');
      setGitUsername('');
      setGitToken('');
      onClose();
    } catch (error) {
      console.error('Error analyzing Git repository:', error);
      toast.error(error instanceof Error ? error.message : 'Git仓库解析失败，请重试');
    } finally {
      setIsAnalyzing(false);
    }
  };
  
  // Close modal when clicking outside
  const modalRef = useRef<HTMLDivElement>(null);
  
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (modalRef.current && !modalRef.current.contains(event.target as Node)) {
        onClose();
      }
    };
    
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      document.body.style.overflow = 'hidden';
    }
    
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.body.style.overflow = '';
    };
  }, [isOpen, onClose]);
  
  if (!isOpen) return null;
  
  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.95 }}
        transition={{ duration: 0.2 }}
        className="bg-white dark:bg-slate-900 rounded-2xl shadow-xl w-full max-w-2xl"
        ref={modalRef}
      >
        {/* Modal Header */}
        <div className="p-6 border-b border-slate-200 dark:border-slate-700 flex justify-between items-center">
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white">
            上传知识库
          </h2>
          <button
            onClick={onClose}
            className="text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200 transition-colors"
          >
            <i className="fa-solid fa-times text-xl"></i>
          </button>
        </div>
        
        {/* Tabs */}
        <div className="flex border-b border-slate-200 dark:border-slate-700">
          <button
            onClick={() => setActiveTab('file')}
            className={cn(
              "flex-1 py-4 px-6 text-center font-medium transition-colors",
              activeTab === 'file'
                ? "text-blue-600 dark:text-blue-400 border-b-2 border-blue-600 dark:border-blue-400"
                : "text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300 border-b-2 border-transparent"
            )}
          >
            上传文件
          </button>
          <button
            onClick={() => setActiveTab('git')}
            className={cn(
              "flex-1 py-4 px-6 text-center font-medium transition-colors",
              activeTab === 'git'
                ? "text-blue-600 dark:text-blue-400 border-b-2 border-blue-600 dark:border-blue-400"
                : "text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300 border-b-2 border-transparent"
            )}
          >
            Git仓库上传
          </button>
        </div>
        
        {/* Form Content */}
        <div className="p-6">
          {activeTab === 'file' ? (
            <div className="space-y-6">
              {/* Mode Toggle Section */}
              <div className="flex items-center gap-2">
                {isInputMode ? (
                  <input
                    type="text"
                    value={tagName}
                    onChange={(e) => setTagName(e.target.value)}
                    placeholder="请输入新的知识库标签名称"
                    className="flex-1 px-4 py-2 rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    disabled={isUploading}
                  />
                ) : (
                  <select
                    value={selectedTagId}
                    onChange={(e) => setSelectedTagId(e.target.value)}
                    className="flex-1 px-4 py-2 rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    disabled={isLoadingTags || isUploading}
                  >
                    <option value="default">...</option>
                    {tagList.map(tag => (
                      <option key={tag.tagId} value={tag.tagId}>
                        {tag.tagName}
                      </option>
                    ))}
                  </select>
                )}
                
                <button
                  onClick={toggleMode}
                  disabled={isUploading}
                  className="px-4 py-2 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors"
                >
                  {isInputMode ? '->补充' : '->新建'}
                </button>
              </div>
              
              {/* File Upload Area */}
              <div
                ref={dropZoneRef}
                className={cn(
                  "border-2 border-dashed rounded-lg p-8 text-center transition-all cursor-pointer",
                  isDragging 
                    ? "border-blue-500 bg-blue-50 dark:bg-blue-900/20" 
                    : "border-slate-300 dark:border-slate-600 hover:border-blue-400 dark:hover:border-blue-500"
                )}
                onClick={() => fileInputRef.current?.click()}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
              >
                <input
                  type="file"
                  ref={fileInputRef}
                  multiple
                  className="hidden"
                  onChange={(e) => handleFileSelect(e.target.files)}
                  disabled={isUploading}
                />
                
                <i className="fa-solid fa-cloud-upload text-4xl text-slate-400 dark:text-slate-500 mb-4"></i>
                <h3 className="text-lg font-medium text-slate-900 dark:text-slate-100 mb-1">
                  拖放文件到此处或点击上传
                </h3>
                <p className="text-sm text-slate-500 dark:text-slate-400 mb-4">
                  支持PDF、TXT、DOCX等格式文件
                </p>
                
                {/* Selected Files List */}
                {files.length > 0 && (
                  <div className="mt-4 bg-slate-50 dark:bg-slate-800 p-3 rounded-lg max-h-40 overflow-y-auto">
                    <p className="text-sm font-medium text-slate-700 dark:text-slate-300 mb-2">
                      已选择 {files.length} 个文件:
                    </p>
                    <ul className="text-left text-sm space-y-1">
                      {files.map((file, index) => (
                        <li key={index} className="flex items-center justify-between">
                          <span className="truncate max-w-[80%]">{file.name}</span>
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              removeFile(index);
                            }}
                            className="text-red-500 hover:text-red-700"
                          >
                            <i className="fa-solid fa-times"></i>
                          </button>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
              
              {/* Upload Button */}
              <button
                onClick={handleFileUpload}
                disabled={isUploading || files.length === 0}
                className={cn(
                  "w-full py-3 px-4 rounded-lg font-medium transition-opacity disabled:opacity-70 disabled:cursor-not-allowed",
                  (isUploading || files.length === 0)
                    ? "bg-slate-300 dark:bg-slate-700 text-white"
                    : "bg-gradient-to-r from-green-600 to-teal-600 text-white hover:opacity-90"
                )}
              >
                {isUploading ? (
                  <>
                    <i className="fa-solid fa-spinner fa-spin mr-2"></i>
                    <span>上传中...</span>
                  </>
                ) : (
                  <>
                    <i className="fa-solid fa-upload mr-2"></i>
                    <span>上传文件</span>
                  </>
                )}
              </button>
            </div>
          ) : (
            <div className="space-y-4">
              {/* Repository URL */}
              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                  仓库地址
                </label>
                <input
                  type="text"
                  value={repoUrl}
                  onChange={(e) => setRepoUrl(e.target.value)}
                  placeholder="https://github.com/example/repo.git"
                  className="w-full px-4 py-2 rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  disabled={isAnalyzing}
                />
              </div>
              
              {/* Git Username */}
              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                  Git用户名
                </label>
                <input
                  type="text"
                  value={gitUsername}
                  onChange={(e) => setGitUsername(e.target.value)}
                  placeholder="Your Git username"
                  className="w-full px-4 py-2 rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  disabled={isAnalyzing}
                />
              </div>
              
              {/* Git Token */}
              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                  Git Token
                </label>
                <input
                  type="password"
                  value={gitToken}
                  onChange={(e) => setGitToken(e.target.value)}
                  placeholder="Your Git personal access token"
                  className="w-full px-4 py-2 rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  disabled={isAnalyzing}
                />
              </div>
              
              {/* Analyze Button */}
              <button
                onClick={handleAnalyzeGitRepo}
                disabled={isAnalyzing}
                className={cn(
                  "w-full py-3 px-4 rounded-lg font-medium transition-opacity disabled:opacity-70 disabled:cursor-not-allowed",
                  isAnalyzing
                    ? "bg-slate-300 dark:bg-slate-700 text-white"
                    : "bg-gradient-to-r from-blue-600 to-indigo-600 text-white hover:opacity-90"
                )}
              >
                {isAnalyzing ? (
                  <>
                    <i className="fa-solid fa-spinner fa-spin mr-2"></i>
                    <span>解析中...</span>
                  </>
                ) : (
                  <>
                    <i className="fa-solid fa-code mr-2"></i>
                    <span>解析仓库</span>
                  </>
                )}
              </button>
            </div>
          )}
        </div>
      </motion.div>
    </div>
  );
}