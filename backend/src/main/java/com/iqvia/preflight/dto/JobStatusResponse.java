package com.iqvia.preflight.dto;



import com.iqvia.preflight.model.JobStatus;



public class JobStatusResponse {



    private final String jobId;

    private final JobStatus status;

    private final int progress;

    private final String message;

    private final PreflightCheckResponse result;

    private final String resultJson;

    private final String error;



    public JobStatusResponse(

            String jobId,

            JobStatus status,

            int progress,

            String message,

            PreflightCheckResponse result,

            String error

    ) {

        this(jobId, status, progress, message, result, null, error);

    }



    public JobStatusResponse(

            String jobId,

            JobStatus status,

            int progress,

            String message,

            PreflightCheckResponse result,

            String resultJson,

            String error

    ) {

        this.jobId = jobId;

        this.status = status;

        this.progress = progress;

        this.message = message;

        this.result = result;

        this.resultJson = resultJson;

        this.error = error;

    }



    public String getJobId() { return jobId; }

    public JobStatus getStatus() { return status; }

    public int getProgress() { return progress; }

    public String getMessage() { return message; }

    public PreflightCheckResponse getResult() { return result; }

    public String getResultJson() { return resultJson; }

    public String getError() { return error; }

}


