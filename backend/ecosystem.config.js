module.exports = {
    apps: [{
        name: 'munandy-backend',
        script: './server.js',
        cwd: __dirname,
        watch: false,
        restart_delay: 3000,
        max_restarts: 10,
        log_file: './logs/combined.log',
        out_file: './logs/out.log',
        error_file: './logs/error.log',
        time: true,
        env: {
            NODE_ENV: 'production',
            PORT: 3000
        }
    }]
};
