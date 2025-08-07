import { motion } from 'framer-motion';

export function TypingIndicator() {
  return (
    <div className="flex items-start gap-3">
      {/* Avatar */}
      <div className="w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 bg-gradient-to-br from-slate-200 to-slate-300 dark:from-slate-700 dark:to-slate-600 text-slate-700 dark:text-slate-200">
        <i className="fa-solid fa-robot"></i>
      </div>
      
      {/* Typing indicator bubble */}
      <div className="bg-white dark:bg-slate-800 p-4 rounded-2xl rounded-tl-none border border-slate-100 dark:border-slate-700">
        <div className="flex gap-1">
          {[1, 2, 3].map((i) => (
            <motion.span
              key={i}
              className="w-2 h-2 rounded-full bg-slate-400 dark:bg-slate-500"
              animate={{
                scale: [1, 0.5, 1],
                opacity: [1, 0.5, 1]
              }}
              transition={{
                duration: 1,
                repeat: Infinity,
                delay: i * 0.2
              }}
            />
          ))}
        </div>
      </div>
    </div>
  );
}