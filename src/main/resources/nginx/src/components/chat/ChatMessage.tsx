import { motion } from 'framer-motion';
import { cn } from '@/lib/utils';

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
          <p className="whitespace-pre-wrap leading-relaxed">{content}</p>
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