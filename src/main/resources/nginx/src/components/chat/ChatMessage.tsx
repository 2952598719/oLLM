import { motion } from 'framer-motion';
import { cn } from '@/lib/utils';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';

interface ChatMessageProps {
  content: string;
  role: 'user' | 'assistant';
  timestamp: Date;
}

export function ChatMessage({ content, role, timestamp }: ChatMessageProps) {
  // Format timestamp to relative time (e.g., "2m ago")
  const formatTimeAgo = (date: Date) => {
    const seconds = Math.floor((new Date().getTime() - date.getTime()) / 1000);
    
    let interval = Math.floor(seconds / 31536000);
    if (interval >= 1) return `${interval}y ago`;
    
    interval = Math.floor(seconds / 2592000);
    if (interval >= 1) return `${interval}mo ago`;
    
    interval = Math.floor(seconds / 86400);
    if (interval >= 1) return `${interval}d ago`;
    
    interval = Math.floor(seconds / 3600);
    if (interval >= 1) return `${interval}h ago`;
    
    interval = Math.floor(seconds / 60);
    if (interval >= 1) return `${interval}m ago`;
    
    return 'Just now';
  };
  
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: 'easeOut' }}
      className={cn(
        "flex items-start gap-3",
        role === 'user' ? "flex-row-reverse" : "flex-row"
      )}
    >
      {/* Avatar */}
      <div className={cn(
        "w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0",
        role === 'user' 
          ? "bg-gradient-to-br from-blue-500 to-indigo-600 text-white" 
          : "bg-gradient-to-br from-slate-200 to-slate-300 dark:from-slate-700 dark:to-slate-600 text-slate-700 dark:text-slate-200"
      )}>
        <i className={role === 'user' ? "fa-solid fa-user" : "fa-solid fa-robot"}></i>
      </div>
      
      {/* Message content */}
      <div className="flex flex-col flex-1 max-w-[80%]">
        {/* Message bubble */}
        <div className={cn(
          "p-4 rounded-2xl shadow-sm",
          role === 'user'
            ? "bg-gradient-to-br from-blue-600 to-indigo-600 text-white rounded-tr-none"
            : "bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 rounded-tl-none border border-slate-100 dark:border-slate-700"
        )}>
           <ReactMarkdown 
             remarkPlugins={[remarkGfm, remarkMath]}
             rehypePlugins={[rehypeKatex]}
            className="prose prose-sm max-w-none leading-relaxed"
            components={{
              p: ({ node, ...props }) => <p {...props} className="my-1 leading-relaxed" />,
              h1: ({ node, ...props }) => <h1 {...props} className="text-xl font-bold mt-4 mb-2" />,
              h2: ({ node, ...props }) => <h2 {...props} className="text-lg font-bold mt-3 mb-1" />,
              h3: ({ node, ...props }) => <h3 {...props} className="text-base font-bold mt-2 mb-1" />,
              code: ({ node, ...props }) => <code {...props} className="bg-slate-100 dark:bg-slate-800 px-1 py-0.5 rounded text-sm" />,
              pre: ({ node, ...props }) => (
                <pre {...props} className="bg-slate-100 dark:bg-slate-800 p-3 rounded-lg overflow-x-auto text-sm my-2" />
              ),
              ul: ({ node, ...props }) => <ul {...props} className="list-disc pl-5 my-1" />,
              ol: ({ node, ...props }) => <ol {...props} className="list-decimal pl-5 my-1" />,
              li: ({ node, ...props }) => <li {...props} className="my-0.5" />,
              a: ({ node, ...props }) => <a {...props} className="text-blue-600 dark:text-blue-400 hover:underline" />,
              strong: ({ node, ...props }) => <strong {...props} className="font-bold" />,
               em: ({ node, ...props }) => <em {...props} className="italic" />,
               // 表格样式
               table: ({ node, ...props }) => (
                 <div className="overflow-x-auto my-4">
                   <table {...props} className="min-w-full border-collapse border border-slate-200 dark:border-slate-700" />
                 </div>
               ),
               th: ({ node, ...props }) => (
                 <th {...props} className="border border-slate-300 dark:border-slate-600 bg-slate-50 dark:bg-slate-800 px-3 py-2 text-left text-sm font-semibold" />
               ),
               td: ({ node, ...props }) => (
                 <td {...props} className="border border-slate-300 dark:border-slate-600 px-3 py-2 text-sm" />
               )
            }}
          >
            {content}
          </ReactMarkdown>
        </div>
        
        {/* Timestamp */}
        <p className={cn(
          "text-xs mt-1",
          role === 'user' ? "text-right text-blue-200/80" : "text-slate-500 dark:text-slate-400"
        )}>
          {formatTimeAgo(timestamp)}
        </p>
      </div>
    </motion.div>
  );
}