import { useState, useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import { cn } from '@/lib/utils';
import { toast } from 'sonner';
import { useContext } from 'react';
import { AuthContext } from '@/contexts/authContext';

interface AuthModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function AuthModal({ isOpen, onClose }: AuthModalProps) {
  const [activeTab, setActiveTab] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [verificationCode, setVerificationCode] = useState('');
  const [captcha, setCaptcha] = useState('');
  const [captchaUrl, setCaptchaUrl] = useState('');
  const [countdown, setCountdown] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [isVerificationCodeLoading, setIsVerificationCodeLoading] = useState(false);
  const { setIsAuthenticated } = useContext(AuthContext);
  
  // Generate a unique timestamp for captcha to prevent caching
  const getCaptchaUrl = () => {
    return `http://localhost:8090/api/v1/user/send_captcha?timestamp=${Date.now()}`;
  };
  
  // Fetch captcha when register tab is active
  useEffect(() => {
    if (activeTab === 'register') {
      setCaptchaUrl(getCaptchaUrl());
    }
  }, [activeTab]);
  
  // Countdown timer for verification code
  useEffect(() => {
    let interval: number;
    if (countdown > 0) {
      interval = setInterval(() => {
        setCountdown(prev => prev - 1);
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [countdown]);
  
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
  
  // Handle login form submission
  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!email || !password) {
      toast.error('请输入邮箱和密码');
      return;
    }
    
    setIsLoading(true);
    
    try {
      const response = await fetch('http://localhost:8090/api/v1/user/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({
          email,
          password,
        }),
        credentials: 'include', // Include cookies for session management
      });
      
      const data = await response.text();
      if (response.ok) {
        toast.success('登录成功');
        setIsAuthenticated(true);
        onClose();
      } else {
        toast.error(data || '登录失败，请检查邮箱和密码');
      }
    } catch (error) {
      toast.error('网络错误，请稍后重试');
      console.error('Login error:', error);
    } finally {
      setIsLoading(false);
    }
  };
  
  // Handle register form submission
  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!email || !password || !verificationCode || !captcha) {
      toast.error('请填写所有必填字段');
      return;
    }
    
    setIsLoading(true);
    
    try {
      const response = await fetch('http://localhost:8090/api/v1/user/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({
          email,
          password,
          verificationCode,
          captcha,
        }),
        credentials: 'include', // Include cookies for session management
      });
      
      const data = await response.text();
			console.log(data)
      
      if (response.ok) {
        toast.success('注册成功，请登录');
        setActiveTab('login');
        // Clear form fields after successful registration
        setEmail('');
        setPassword('');
        setVerificationCode('');
        setCaptcha('');
      } else {
        toast.error(data || '注册失败，请检查信息');
        // Refresh captcha on registration failure
        setCaptchaUrl(getCaptchaUrl());
      }
    } catch (error) {
      toast.error('网络错误，请稍后重试');
      console.error('Register error:', error);
    } finally {
      setIsLoading(false);
    }
  };
  
  // Send verification code
  const sendVerificationCode = async () => {
    if (!email) {
      toast.error('请先输入邮箱');
      return;
    }
    
    // Simple email validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      toast.error('请输入有效的邮箱地址');
      return;
    }
     
  setIsVerificationCodeLoading(true);
     
    try {
      const response = await fetch(`http://localhost:8090/api/v1/user/send_email?email=${encodeURIComponent(email)}&type=register`, {
        method: 'GET',
        credentials: 'include', // Include cookies for session management
      });
      
      const data = await response.text();
      
      if (response.ok) {
        toast.success('验证码已发送到您的邮箱');
        setCountdown(60); // Start 60s countdown
      } else {
        toast.error(data || '发送验证码失败');
      }
    } catch (error) {
      toast.error('网络错误，请稍后重试');
      console.error('Send verification code error:', error);
     } finally {
      setIsVerificationCodeLoading(false);
    }
  };
  
  // Refresh captcha
  const refreshCaptcha = () => {
    setCaptchaUrl(getCaptchaUrl());
    setCaptcha(''); // Clear input when refreshing
  };
  
  if (!isOpen) return null;
  
  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.95 }}
        transition={{ duration: 0.2 }}
        className="bg-white dark:bg-slate-900 rounded-2xl shadow-xl w-full max-w-md"
        ref={modalRef}
      >
        {/* Modal Header */}
        <div className="p-6 border-b border-slate-200 dark:border-slate-700 flex justify-between items-center">
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white">
            {activeTab === 'login' ? '登录' : '注册'}
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
            onClick={() => {
              setActiveTab('login');
              setEmail('');
              setPassword('');
            }}
            className={cn(
              "flex-1 py-4 px-6 text-center font-medium transition-colors",
              activeTab === 'login'
                ? "text-blue-600 dark:text-blue-400 border-b-2 border-blue-600 dark:border-blue-400"
                : "text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300 border-b-2 border-transparent"
            )}
          >
            登录
          </button>
          <button
            onClick={() => {
              setActiveTab('register');
              setEmail('');
              setPassword('');
              setVerificationCode('');
              setCaptcha('');
              setCountdown(0);
            }}
            className={cn(
              "flex-1 py-4 px-6 text-center font-medium transition-colors",
              activeTab === 'register'
                ? "text-blue-600 dark:text-blue-400 border-b-2 border-blue-600 dark:border-blue-400"
                : "text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300 border-b-2 border-transparent"
            )}
          >
            注册
          </button>
        </div>
        
        {/* Form Content */}
        <div className="p-6">
          {activeTab === 'login' ? (
            <form onSubmit={handleLogin} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                  邮箱
                </label>
                 <input
                   type="email"
                   value={email}
                   onChange={(e) => setEmail(e.target.value)}
                   className="w-full px-4 py-2 rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-50０"
                   placeholder="请输入邮箱"  
                    disabled={isLoading}
                 />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                  密码
                </label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full px-4 py-2 rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="请输入密码"
                  disabled={isLoading}
                />
              </div>
              
              <button
                type="submit"
                disabled={isLoading}
                className="w-full py-3 px-4 bg-gradient-to-r from-blue-600 to-indigo-600 text-white font-medium rounded-lg hover:opacity-90 transition-opacity disabled:opacity-70 disabled:cursor-not-allowed flex items-center justify-center gap-2"
              >
                {isLoading ? (
                  <>
                    <i className="fa-solid fa-spinner fa-spin"></i>
                    <span>登录中...</span>
                  </>
                ) : (
                  <span>登录</span>
                )}
              </button>
            </form>
          ) : (
            <form onSubmit={handleRegister} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                  邮箱
                </label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-4 py-2 rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="请输入邮箱"
                  disabled={isLoading || countdown > 0}
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                  密码
                </label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full px-4 py-2 rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="请输入密码"
                  disabled={isLoading}
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                  验证码
                </label>
                 <div className="flex gap-2">
                   <input
                     type="text"
                     value={verificationCode}
                     onChange={(e) => setVerificationCode(e.target.value)}
                     className="flex-1 px-4 py-2 rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                     placeholder="请输入验证码"
                     disabled={isLoading}
                   />
                  <button
                    type="button"
                    onClick={sendVerificationCode}
                     disabled={isVerificationCodeLoading || countdown > 0 || !email}
                    className="px-4 py-2 text-sm font-medium rounded-lg transition-colors disabled:opacity-70 disabled:cursor-not-allowed"
                    style={{
                      backgroundColor: countdown > 0 || isVerificationCodeLoading ? '#6b7280' : '#3b82f6',
                      color: 'white',
                    }}
                  >
                    {isVerificationCodeLoading ? (
                      <>
                        <i className="fa-solid fa-spinner fa-spin mr-1"></i>
                        <span>发送中...</span>
                      </>
                    ) : countdown > 0 ? (
                      `${countdown}秒`
                    ) : (
                      '发送验证码'
                    )}
                  </button>
                </div>
              </div>
              
              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                  图形验证码
                </label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={captcha}
                    onChange={(e) => setCaptcha(e.target.value)}
                    className="flex-1 px-4 py-2 rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="请输入图形验证码"
                    disabled={isLoading}
                  />
                  <button
                    type="button"
                    onClick={refreshCaptcha}
                    disabled={isLoading}
                    className="px-2 py-2 rounded-lg border border-slate-300 dark:border-slate-600 bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors"
                  >
                    <img
                      src={captchaUrl}
                      alt="Captcha"
                      className="h-10 w-auto object-contain"
                      onError={refreshCaptcha} // Refresh if image fails to load
                    />
                  </button>
                </div>
              </div>
              
              <button
                type="submit"
                disabled={isLoading}
                className="w-full py-3 px-4 bg-gradient-to-r from-blue-600 to-indigo-600 text-white font-medium rounded-lg hover:opacity-90 transition-opacity disabled:opacity-70 disabled:cursor-not-allowed flex items-center justify-center gap-2"
              >
                {isLoading ? (
                  <>
                    <i className="fa-solid fa-spinner fa-spin"></i>
                    <span>注册中...</span>
                  </>
                ) : (
                  <span>注册</span>
                )}
              </button>
            </form>
          )}
        </div>
      </motion.div>
    </div>
  );
}